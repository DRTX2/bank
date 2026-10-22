package com.drtx.bank.ledger;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.drtx.bank.accounts.Account;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

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

    private final JournalRepository journals;
    private final PostingRepository postings;
    private final EntityManager em;

    LedgerService(JournalRepository journals, PostingRepository postings, EntityManager em) {
        this.journals = journals;
        this.postings = postings;
        this.em = em;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry post(UUID tenantId, JournalEntry.Type type, String reference, String description,
                             String currency, List<Leg> legs) {
        if (legs.size() < 2 || legs.stream().mapToLong(Leg::amountMinor).sum() != 0
                || legs.stream().anyMatch(l -> l.amountMinor() == 0)) {
            throw new IllegalArgumentException("Unbalanced journal entry " + legs);
        }
        // Lock by refresh, not by query: a caller may already hold these accounts (e.g. the teller flow read the
        // account and the vault), and a locking query on a cached, stale instance fails its version check, which
        // turned two simultaneous deposits into a 409. refresh(PESSIMISTIC_WRITE) re-reads the row FOR UPDATE.
        // Ordered by the textual UUID, which matches PostgreSQL's uuid order used by lockAllOrdered: no deadlocks.
        Map<UUID, Account> locked = new HashMap<>();
        legs.stream().map(Leg::accountId).distinct().sorted(Comparator.comparing(UUID::toString)).forEach(id -> {
            Account account = em.find(Account.class, id);
            if (account == null) {
                throw DomainException.notFound("Account");
            }
            em.refresh(account, LockModeType.PESSIMISTIC_WRITE);
            locked.put(id, account);
        });
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
