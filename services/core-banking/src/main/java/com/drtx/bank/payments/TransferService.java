package com.drtx.bank.payments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;

import com.drtx.bank.accounts.Account;
import com.drtx.bank.accounts.AccountNumbers;
import com.drtx.bank.accounts.AccountRepository;
import com.drtx.bank.ledger.JournalEntry;
import com.drtx.bank.ledger.LedgerService;
import com.drtx.bank.payments.risk.RiskAssessment;
import com.drtx.bank.payments.risk.RiskClient;
import com.drtx.bank.platform.Tenant;
import com.drtx.bank.platform.TenantRepository;
import com.drtx.bank.shared.audit.AuditService;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.outbox.OutboxWriter;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.Permission;
import com.drtx.bank.shared.tenancy.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates a transfer: validation → idempotent intake → risk scoring (gRPC, outside any DB transaction)
 * → maker-checker / review gates → ledger booking + outbox event, atomically.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final TenantRepository tenants;
    private final LedgerService ledger;
    private final RiskClient risk;
    private final OutboxWriter outbox;
    private final AuditService audit;
    private final MeterRegistry meters;
    private final TransactionTemplate tx;
    private final String topic;

    TransferService(TransferRepository transfers, AccountRepository accounts, TenantRepository tenants,
                    LedgerService ledger, RiskClient risk, OutboxWriter outbox, AuditService audit,
                    MeterRegistry meters, PlatformTransactionManager txManager,
                    @Value("${bank.kafka.payments-topic}") String topic) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.tenants = tenants;
        this.ledger = ledger;
        this.risk = risk;
        this.outbox = outbox;
        this.audit = audit;
        this.meters = meters;
        this.tx = new TransactionTemplate(txManager);
        this.topic = topic;
    }

    public record CreateTransfer(UUID sourceAccountId, String destinationNumber, long amountMinor,
                                 String remittanceInfo, String purposeCode, String endToEndId,
                                 String idempotencyKey, UUID batchId) {
    }

    private record Intake(Account source, Account destination, long approvalThresholdMinor) {
    }

    public TransferRow create(CurrentUser actor, CreateTransfer cmd) {
        String hash = requestHash(cmd);
        Optional<TransferRow> replay = replay(actor, cmd.idempotencyKey(), hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        Intake intake = tx.execute(s -> validate(actor, cmd));
        UUID transferId = UUID.randomUUID();
        RiskAssessment assessment = risk.score(new RiskClient.Request(actor.tenantId(), transferId,
                intake.source().getId(), intake.destination().getId(), cmd.amountMinor(),
                intake.source().getCurrency(), actor.id()));
        Transfer created;
        try {
            created = tx.execute(s -> {
                Transfer t = new Transfer(transferId, actor.tenantId(), cmd.idempotencyKey(), hash, actor.id(),
                        intake.source().getId(), intake.destination().getId(), cmd.amountMinor(),
                        intake.source().getCurrency(), endToEndId(cmd), cmd.remittanceInfo(), cmd.purposeCode(),
                        cmd.batchId());
                t.decide(assessment, intake.approvalThresholdMinor());
                transfers.saveAndFlush(t);
                audit.record(actor.tenantId(), actor, "TRANSFER_" + t.getStatus(), "transfer", t.getId(),
                        "amount=" + t.getAmountMinor() + " risk=" + t.getRiskScore() + " " + t.getRiskSignals());
                if (t.getStatus() != Transfer.Status.ACCEPTED) {
                    publish(t.getStatus() == Transfer.Status.REJECTED ? "TransferRejected" : "TransferAwaitingAction", t);
                }
                return t;
            });
        } catch (DataIntegrityViolationException race) {
            // Concurrent retry with the same Idempotency-Key won the insert: return its result.
            return replay(actor, cmd.idempotencyKey(), hash).orElseThrow(() -> race);
        }
        meters.counter("kuntur.transfers.intake", "decision", assessment.decision().name()).increment();
        if (created.getStatus() == Transfer.Status.ACCEPTED) {
            execute(created.getId());
        }
        return row(created.getId());
    }

    private Optional<TransferRow> replay(CurrentUser actor, String key, String hash) {
        return tx.execute(s -> transfers.findByInitiatedByAndIdempotencyKey(actor.id(), key).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT,
                        "Idempotency-Key already used with a different payload");
            }
            return transfers.row(existing.getId()).orElseThrow();
        }));
    }

    private Intake validate(CurrentUser actor, CreateTransfer cmd) {
        Account source = accounts.findById(cmd.sourceAccountId())
                .filter(a -> a.getType() != Account.Type.INTERNAL)
                .filter(a -> actor.has(Permission.TRANSFERS_CREATE_ANY)
                        || (actor.has(Permission.TRANSFERS_CREATE_OWN) && a.isOwnedBy(actor.id())))
                .orElseThrow(() -> DomainException.notFound("Source account"));
        if (!AccountNumbers.isValid(cmd.destinationNumber())) {
            throw new DomainException(ErrorCode.INVALID_ACCOUNT_NUMBER, "Invalid destination account number");
        }
        Account destination = accounts.findByNumber(AccountNumbers.normalize(cmd.destinationNumber()))
                .filter(a -> a.getType() != Account.Type.INTERNAL)
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_ACCOUNT_NUMBER, "Destination account not found"));
        if (source.getId().equals(destination.getId())) {
            throw new DomainException(ErrorCode.VALIDATION, "Source and destination must differ");
        }
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new DomainException(ErrorCode.CURRENCY_MISMATCH, "Cross-currency transfers are not supported");
        }
        if (source.getStatus() != Account.Status.ACTIVE) {
            throw new DomainException(source.getStatus() == Account.Status.CLOSED ? ErrorCode.ACCOUNT_CLOSED
                    : ErrorCode.ACCOUNT_BLOCKED, "Source account is " + source.getStatus());
        }
        if (destination.getStatus() == Account.Status.CLOSED) {
            throw new DomainException(ErrorCode.ACCOUNT_CLOSED, "Destination account is closed");
        }
        Tenant tenant = tenants.findById(actor.tenantId()).orElseThrow();
        return new Intake(source, destination, tenant.getApprovalThresholdMinor());
    }

    /** Books an ACCEPTED transfer. Safe to call repeatedly: only the first call moves money. */
    TransferRow execute(UUID transferId) {
        try {
            tx.executeWithoutResult(s -> {
                Transfer t = transfers.lockById(transferId).orElseThrow();
                if (t.getStatus() != Transfer.Status.ACCEPTED) {
                    return;
                }
                List<Account> locked = accounts.lockAllOrdered(List.of(t.getSourceAccountId(), t.getDestinationAccountId()));
                Account source = locked.stream().filter(a -> a.getId().equals(t.getSourceAccountId())).findFirst().orElseThrow();
                Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
                long spentToday = transfers.sumCompletedOutgoingSince(source.getId(), startOfDay);
                if (spentToday + t.getAmountMinor() > source.getDailyLimitMinor()) {
                    throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "Daily limit exceeded");
                }
                JournalEntry journal = ledger.post(t.getTenantId(), JournalEntry.Type.TRANSFER, t.getId().toString(),
                        t.getRemittanceInfo() == null ? "Transferencia" : t.getRemittanceInfo(), t.getCurrency(),
                        List.of(new LedgerService.Leg(t.getSourceAccountId(), -t.getAmountMinor()),
                                new LedgerService.Leg(t.getDestinationAccountId(), t.getAmountMinor())));
                t.complete(journal.getId());
                transfers.flush();
                publish("TransferCompleted", t);
                meters.counter("kuntur.transfers.completed").increment();
            });
        } catch (DomainException e) {
            tx.executeWithoutResult(s -> {
                Transfer t = transfers.lockById(transferId).orElseThrow();
                t.fail(e.code(), e.getMessage());
                transfers.flush();
                audit.record(t.getTenantId(), null, "TRANSFER_FAILED", "transfer", t.getId(), e.code().isoReason());
                publish("TransferFailed", t);
                meters.counter("kuntur.transfers.failed", "reason", String.valueOf(e.code().isoReason())).increment();
            });
        }
        return row(transferId);
    }

    public TransferRow approve(CurrentUser approver, UUID id, String note) {
        tx.executeWithoutResult(s -> {
            Transfer t = transfers.lockById(id).orElseThrow(() -> DomainException.notFound("Transfer"));
            t.approve(approver.id(), note);
            audit.record(approver.tenantId(), approver, "TRANSFER_APPROVED", "transfer", id, note);
        });
        return execute(id);
    }

    public TransferRow reject(CurrentUser approver, UUID id, String note) {
        tx.executeWithoutResult(s -> {
            Transfer t = transfers.lockById(id).orElseThrow(() -> DomainException.notFound("Transfer"));
            t.decline(approver.id(), note, Transfer.Status.PENDING_APPROVAL, ErrorCode.FORBIDDEN);
            audit.record(approver.tenantId(), approver, "TRANSFER_REJECTED", "transfer", id, note);
            publish("TransferRejected", t);
        });
        return row(id);
    }

    /** Compliance decision on a transfer held by the risk engine. */
    public TransferRow review(CurrentUser officer, UUID id, boolean release, String note) {
        Transfer.Status after = tx.execute(s -> {
            Transfer t = transfers.lockById(id).orElseThrow(() -> DomainException.notFound("Transfer"));
            if (release) {
                long threshold = tenants.findById(officer.tenantId()).orElseThrow().getApprovalThresholdMinor();
                t.release(officer.id(), note, threshold);
                audit.record(officer.tenantId(), officer, "RISK_RELEASED", "transfer", id, note);
            } else {
                t.decline(officer.id(), note, Transfer.Status.HELD_FOR_REVIEW, ErrorCode.FRAUD_SUSPECTED);
                audit.record(officer.tenantId(), officer, "RISK_BLOCKED", "transfer", id, note);
                publish("TransferRejected", t);
            }
            return t.getStatus();
        });
        return after == Transfer.Status.ACCEPTED ? execute(id) : row(id);
    }

    public List<TransferRow> search(CurrentUser user, Transfer.Status status, int page, int size) {
        UUID owner = user.has(Permission.TRANSFERS_READ_ANY) ? null : user.id();
        return tx.execute(s -> transfers.search(status, owner, PageRequest.of(page, size)));
    }

    public TransferRow readable(CurrentUser user, UUID id) {
        TransferRow row = tx.execute(s -> transfers.row(id)).orElseThrow(() -> DomainException.notFound("Transfer"));
        boolean own = user.id().equals(row.sourceOwnerId()) || user.id().equals(row.destinationOwnerId());
        if (!user.has(Permission.TRANSFERS_READ_ANY) && !(own && user.has(Permission.TRANSFERS_READ_OWN))) {
            throw DomainException.notFound("Transfer");
        }
        return row;
    }

    public List<Object[]> statsSince(Instant since) {
        return tx.execute(s -> transfers.statsSince(since));
    }

    public List<Transfer> batchTransfers(UUID batchId) {
        return tx.execute(s -> transfers.findByBatchIdOrderByCreatedAt(batchId));
    }

    /**
     * Crash recovery: a transfer can be ACCEPTED but not yet booked if the process died in between.
     * {@link #execute} is idempotent, so the sweep simply retries.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void recoverStuckTransfers() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.MINUTES);
        List<Transfer> stuck = TenantContext.callAs(TenantContext.SYSTEM,
                () -> tx.execute(s -> transfers.findTop50ByStatusAndUpdatedAtBefore(Transfer.Status.ACCEPTED, cutoff)));
        for (Transfer t : stuck) {
            log.info("Recovering transfer {} stuck in ACCEPTED", t.getId());
            TenantContext.runAs(t.getTenantId(), () -> execute(t.getId()));
        }
    }

    private TransferRow row(UUID id) {
        return tx.execute(s -> transfers.row(id)).orElseThrow();
    }

    private void publish(String type, Transfer t) {
        TransferRow row = transfers.row(t.getId()).orElseThrow();
        outbox.append(t.getTenantId(), topic, "transfer", t.getId(), type, PaymentEvent.of(type, row));
    }

    private static String endToEndId(CreateTransfer cmd) {
        if (cmd.endToEndId() != null && !cmd.endToEndId().isBlank()) {
            return cmd.endToEndId();
        }
        return "E2E" + UUID.randomUUID().toString().replace("-", "").substring(0, 29).toUpperCase();
    }

    static String requestHash(CreateTransfer c) {
        String canonical = String.join("|", String.valueOf(c.sourceAccountId()),
                AccountNumbers.normalize(c.destinationNumber()), String.valueOf(c.amountMinor()),
                String.valueOf(c.remittanceInfo()), String.valueOf(c.purposeCode()), String.valueOf(c.endToEndId()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
