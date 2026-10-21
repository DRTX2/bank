package com.drtx.bank.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.drtx.bank.accounts.Account;
import com.drtx.bank.accounts.AccountRepository;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Double-entry ledger. Every journal entry is balanced (legs sum to zero), accounts are locked in a stable
 * order, and balances move only here. Postings are append-only, so history is never rewritten: corrections
 * are new, compensating entries.
 */
@Service
public class LedgerService {

    public record Leg(UUID accountId, long amountMinor) {
    }

    private final AccountRepository accounts;
    private final JournalRepository journals;
    private final PostingRepository postings;

    LedgerService(AccountRepository accounts, JournalRepository journals, PostingRepository postings) {
        this.accounts = accounts;
        this.journals = journals;
        this.postings = postings;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry post(UUID tenantId, JournalEntry.Type type, String reference, String description,
                             String currency, List<Leg> legs) {
        if (legs.size() < 2 || legs.stream().mapToLong(Leg::amountMinor).sum() != 0
                || legs.stream().anyMatch(l -> l.amountMinor() == 0)) {
            throw new IllegalArgumentException("Unbalanced journal entry " + legs);
        }
        Map<UUID, Account> locked = accounts.lockAllOrdered(legs.stream().map(Leg::accountId).toList()).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        if (locked.size() != legs.stream().map(Leg::accountId).distinct().count()) {
            throw DomainException.notFound("Account");
        }
        JournalEntry journal = journals.save(new JournalEntry(tenantId, type, reference, description));
        Instant now = journal.getCreatedAt();
        // Debits first: a failing debit aborts before any credit is applied in memory.
        legs.stream().sorted((a, b) -> Long.compare(a.amountMinor(), b.amountMinor())).forEach(leg -> {
            Account account = locked.get(leg.accountId());
            if (!account.getCurrency().equals(currency)) {
                throw new DomainException(ErrorCode.CURRENCY_MISMATCH, "Currency mismatch on " + account.getNumber());
            }
            long after = account.apply(leg.amountMinor());
            postings.save(new Posting(tenantId, journal.getId(), account.getId(), leg.amountMinor(), after, now));
        });
        return journal;
    }

    @Transactional(readOnly = true)
    public List<MovementView> movements(UUID accountId, Instant from, Instant to) {
        return postings.movements(accountId, from, to);
    }

    @Transactional(readOnly = true)
    public long balanceAt(UUID accountId, Instant at) {
        return postings.lastBalanceBefore(accountId, at).stream().findFirst().orElse(0L);
    }
}
