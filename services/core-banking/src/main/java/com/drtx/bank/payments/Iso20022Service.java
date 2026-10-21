package com.drtx.bank.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.drtx.bank.accounts.Account;
import com.drtx.bank.accounts.AccountNumbers;
import com.drtx.bank.accounts.AccountRepository;
import com.drtx.bank.accounts.AccountService;
import com.drtx.bank.identity.UserService;
import com.drtx.bank.ledger.JournalEntry;
import com.drtx.bank.ledger.LedgerService;
import com.drtx.bank.ledger.MovementView;
import com.drtx.bank.payments.iso20022.Iso20022Messages;
import com.drtx.bank.payments.iso20022.Iso20022Messages.TxStatus;
import com.drtx.bank.payments.iso20022.Pain001;
import com.drtx.bank.payments.iso20022.Pain001Parser;
import com.drtx.bank.platform.Tenant;
import com.drtx.bank.platform.TenantRepository;
import com.drtx.bank.shared.audit.AuditService;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.Permission;
import com.drtx.bank.shared.web.Amounts;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class Iso20022Service {

    private final TransferService transfers;
    private final TransferRepository transferRepo;
    private final PaymentBatchRepository batches;
    private final AccountRepository accountRepo;
    private final AccountService accounts;
    private final TenantRepository tenants;
    private final UserService users;
    private final LedgerService ledger;
    private final AuditService audit;
    private final TransactionTemplate tx;

    Iso20022Service(TransferService transfers, TransferRepository transferRepo, PaymentBatchRepository batches,
                    AccountRepository accountRepo, AccountService accounts, TenantRepository tenants,
                    UserService users, LedgerService ledger, AuditService audit, PlatformTransactionManager txm) {
        this.transfers = transfers;
        this.transferRepo = transferRepo;
        this.batches = batches;
        this.accountRepo = accountRepo;
        this.accounts = accounts;
        this.tenants = tenants;
        this.users = users;
        this.ledger = ledger;
        this.audit = audit;
        this.tx = new TransactionTemplate(txm);
    }

    /**
     * Processes a pain.001 bulk file. Each transaction goes through the normal transfer pipeline (risk, limits,
     * four-eyes), so the batch is not atomic: like real banks, the answer is a per-transaction pain.002 report.
     */
    public Iso20022Messages.StatusReport processPain001(CurrentUser actor, byte[] xml) {
        Pain001 message = Pain001Parser.parse(xml);
        Tenant tenant = tx.execute(s -> tenants.findById(actor.tenantId()).orElseThrow());
        PaymentBatch batch;
        try {
            batch = tx.execute(s -> {
                if (batches.existsByMessageId(message.messageId())) {
                    throw new DomainException(ErrorCode.DUPLICATE_MESSAGE, "MsgId " + message.messageId() + " already processed");
                }
                PaymentBatch b = batches.saveAndFlush(new PaymentBatch(actor.tenantId(), message.messageId(), actor.id(),
                        message.numberOfTransactions(), Amounts.toMinor(message.controlSum())));
                audit.record(actor.tenantId(), actor, "PAIN001_RECEIVED", "batch", b.getId(),
                        message.messageId() + " txs=" + message.numberOfTransactions());
                return b;
            });
        } catch (DataIntegrityViolationException e) {
            throw new DomainException(ErrorCode.DUPLICATE_MESSAGE, "MsgId " + message.messageId() + " already processed");
        }

        List<Iso20022Messages.InstructionReport> instructionReports = new ArrayList<>();
        for (Pain001.Instruction instruction : message.instructions()) {
            Optional<Account> debtor = tx.execute(s -> accountRepo.findByNumber(AccountNumbers.normalize(instruction.debtorAccount())));
            List<Iso20022Messages.TxReport> reports = new ArrayList<>();
            for (Pain001.CreditTransfer ct : instruction.transfers()) {
                reports.add(processOne(actor, message.messageId(), batch.getId(), debtor, ct));
            }
            instructionReports.add(new Iso20022Messages.InstructionReport(instruction.paymentInfoId(), reports));
        }
        String groupStatus = groupStatus(instructionReports);
        tx.executeWithoutResult(s -> batches.findById(batch.getId()).orElseThrow().finish(groupStatus));
        return new Iso20022Messages.StatusReport("PAIN002-" + message.messageId(), message.messageId(),
                message.numberOfTransactions(), message.controlSum(), groupStatus, tenant.getBic(), Instant.now(),
                instructionReports);
    }

    private Iso20022Messages.TxReport processOne(CurrentUser actor, String msgId, UUID batchId, Optional<Account> debtor,
                                                 Pain001.CreditTransfer ct) {
        try {
            Account source = debtor.orElseThrow(() ->
                    new DomainException(ErrorCode.INVALID_ACCOUNT_NUMBER, "Debtor account not found"));
            if (!source.getCurrency().equals(ct.currency())) {
                throw new DomainException(ErrorCode.CURRENCY_MISMATCH, "Currency " + ct.currency() + " not supported");
            }
            TransferRow row = transfers.create(actor, new TransferService.CreateTransfer(source.getId(),
                    ct.creditorAccount(), Amounts.toMinor(ct.amount()), ct.remittance(), ct.purpose(),
                    ct.endToEndId(), "pain001:" + msgId + ":" + ct.endToEndId(), batchId));
            Transfer t = row.transfer();
            TxStatus status = switch (t.getStatus()) {
                case COMPLETED -> TxStatus.ACSC;
                case ACCEPTED -> TxStatus.ACCP;
                case PENDING_APPROVAL, HELD_FOR_REVIEW -> TxStatus.PDNG;
                case FAILED, REJECTED -> TxStatus.RJCT;
            };
            return new Iso20022Messages.TxReport(ct.endToEndId(), status,
                    status == TxStatus.RJCT ? t.getFailureCode() : null,
                    status == TxStatus.RJCT ? t.getFailureReason() : null, ct.amount(), ct.currency(), t.getId());
        } catch (DomainException e) {
            String reason = e.code().isoReason() != null ? e.code().isoReason() : "NARR";
            return new Iso20022Messages.TxReport(ct.endToEndId(), TxStatus.RJCT, reason, e.getMessage(), ct.amount(),
                    ct.currency(), null);
        }
    }

    static String groupStatus(List<Iso20022Messages.InstructionReport> reports) {
        List<TxStatus> all = reports.stream().flatMap(r -> r.transactions().stream()).map(Iso20022Messages.TxReport::status).toList();
        if (all.stream().allMatch(s -> s == TxStatus.ACSC)) {
            return "ACSC";
        }
        if (all.stream().allMatch(s -> s == TxStatus.RJCT)) {
            return "RJCT";
        }
        return all.contains(TxStatus.RJCT) ? "PART" : "ACCP";
    }

    public List<PaymentBatch> batches(CurrentUser user) {
        return tx.execute(s -> user.has(Permission.TRANSFERS_READ_ANY) ? batches.findAllByOrderByCreatedAtDesc()
                : batches.findByCreatedByOrderByCreatedAtDesc(user.id()));
    }

    public String pacs008(CurrentUser user, UUID transferId) {
        TransferRow row = transfers.readable(user, transferId);
        Transfer t = row.transfer();
        return tx.execute(s -> {
            Tenant tenant = tenants.findById(t.getTenantId()).orElseThrow();
            return Iso20022Messages.pacs008(new Iso20022Messages.CreditTransfer(t.getId(), t.getEndToEndId(), t.getUetr(),
                    Amounts.fromMinor(t.getAmountMinor()), t.getCurrency(), t.getUpdatedAt(),
                    ownerName(row.sourceOwnerId()), row.sourceNumber(), ownerName(row.destinationOwnerId()),
                    row.destinationNumber(), tenant.getBic(), t.getPurposeCode(), t.getRemittanceInfo()));
        });
    }

    public String camt053(CurrentUser user, UUID accountId, Instant from, Instant to) {
        if (!user.has(Permission.STATEMENTS_READ_ANY) && !user.has(Permission.STATEMENTS_READ_OWN)) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Not allowed to read statements");
        }
        Account account = accounts.readable(user, accountId);
        if (!user.has(Permission.STATEMENTS_READ_ANY) && !account.isOwnedBy(user.id())) {
            throw DomainException.notFound("Account");
        }
        return tx.execute(s -> {
            Tenant tenant = tenants.findById(account.getTenantId()).orElseThrow();
            List<MovementView> movements = ledger.movements(accountId, from, to).reversed();
            List<UUID> transferIds = movements.stream().filter(m -> m.type() == JournalEntry.Type.TRANSFER)
                    .map(m -> UUID.fromString(m.reference())).toList();
            Map<UUID, Transfer> byId = transferRepo.findAllById(transferIds).stream()
                    .collect(Collectors.toMap(Transfer::getId, Function.identity()));
            List<Iso20022Messages.StatementEntry> entries = movements.stream().map(m -> {
                Transfer t = m.type() == JournalEntry.Type.TRANSFER ? byId.get(UUID.fromString(m.reference())) : null;
                return new Iso20022Messages.StatementEntry("P" + m.postingId(),
                        BigDecimal.valueOf(m.amountMinor(), 2), m.bookedAt(), m.type().name(),
                        t == null ? null : t.getEndToEndId(), t == null ? null : t.getUetr(), m.description());
            }).toList();
            long opening = ledger.balanceAt(accountId, from);
            long closing = movements.isEmpty() ? opening : movements.getLast().balanceAfterMinor();
            return Iso20022Messages.camt053(new Iso20022Messages.Statement(account.getNumber(), account.getCurrency(),
                    ownerName(account.getOwnerId()), tenant.getBic(), from, to, BigDecimal.valueOf(opening, 2),
                    BigDecimal.valueOf(closing, 2), entries));
        });
    }

    private String ownerName(UUID ownerId) {
        return ownerId == null ? "Kuntur" : users.get(ownerId).getFullName();
    }
}
