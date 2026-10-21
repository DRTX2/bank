package com.drtx.bank.payments;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.drtx.bank.payments.risk.RiskAssessment;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.tenancy.TenantOwned;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Credit transfer with ISO 20022 identifiers (EndToEndId, UETR). State machine:
 * <pre>
 *   intake ─┬─ risk DECLINE ───────────────────────────────► REJECTED (FRAD)
 *           ├─ risk REVIEW ──► HELD_FOR_REVIEW ─ release ─┐  block ─► REJECTED
 *           ├─ amount ≥ threshold ──► PENDING_APPROVAL ◄──┘ (if still above threshold)
 *           │                        approve (4-eyes) ─► ACCEPTED   reject ─► REJECTED
 *           └─ otherwise ─► ACCEPTED ─► ledger ─► COMPLETED | FAILED (AM04, AM02, AC06…)
 * </pre>
 */
@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer extends TenantOwned {

    public enum Status { PENDING_APPROVAL, HELD_FOR_REVIEW, ACCEPTED, COMPLETED, FAILED, REJECTED }

    @Id
    private UUID id;
    private String idempotencyKey;
    private String requestHash;
    private UUID initiatedBy;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private long amountMinor;
    private String currency;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String endToEndId;
    private UUID uetr;
    private String remittanceInfo;
    private String purposeCode;
    private Integer riskScore;
    private String riskSignals;
    private UUID journalId;
    private UUID batchId;
    private UUID reviewedBy;
    private String reviewNote;
    private String failureCode;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private long version;

    Transfer(UUID id, UUID tenantId, String idempotencyKey, String requestHash, UUID initiatedBy, UUID source,
             UUID destination, long amountMinor, String currency, String endToEndId, String remittanceInfo,
             String purposeCode, UUID batchId) {
        this.id = id;
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.initiatedBy = initiatedBy;
        this.sourceAccountId = source;
        this.destinationAccountId = destination;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.endToEndId = endToEndId;
        this.uetr = UUID.randomUUID();
        this.remittanceInfo = remittanceInfo;
        this.purposeCode = purposeCode;
        this.batchId = batchId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    void decide(RiskAssessment risk, long approvalThresholdMinor) {
        this.riskScore = risk.score();
        this.riskSignals = String.join(",", risk.signals());
        switch (risk.decision()) {
            case DECLINE -> reject(ErrorCode.FRAUD_SUSPECTED, "Declined by risk engine (score " + risk.score() + ")");
            case REVIEW -> moveTo(Status.HELD_FOR_REVIEW);
            case APPROVE -> moveTo(amountMinor >= approvalThresholdMinor ? Status.PENDING_APPROVAL : Status.ACCEPTED);
        }
    }

    void approve(UUID approver, String note) {
        require(Status.PENDING_APPROVAL);
        checkFourEyes(approver);
        review(approver, note);
        moveTo(Status.ACCEPTED);
    }

    void release(UUID officer, String note, long approvalThresholdMinor) {
        require(Status.HELD_FOR_REVIEW);
        checkFourEyes(officer);
        review(officer, note);
        moveTo(amountMinor >= approvalThresholdMinor ? Status.PENDING_APPROVAL : Status.ACCEPTED);
    }

    void decline(UUID reviewer, String note, Status expected, ErrorCode reason) {
        require(expected);
        checkFourEyes(reviewer);
        review(reviewer, note);
        reject(reason, note);
    }

    void complete(UUID journalId) {
        require(Status.ACCEPTED);
        this.journalId = journalId;
        moveTo(Status.COMPLETED);
    }

    void fail(ErrorCode code, String reason) {
        require(Status.ACCEPTED);
        this.failureCode = code.isoReason();
        this.failureReason = reason;
        moveTo(Status.FAILED);
    }

    public boolean isFinal() {
        return status == Status.COMPLETED || status == Status.FAILED || status == Status.REJECTED;
    }

    private void reject(ErrorCode code, String reason) {
        this.failureCode = code.isoReason() == null ? "MS03" : code.isoReason();
        this.failureReason = reason;
        moveTo(Status.REJECTED);
    }

    /** Segregation of duties: whoever initiated a transfer can never be the one who authorises it. */
    private void checkFourEyes(UUID reviewer) {
        if (reviewer.equals(initiatedBy)) {
            throw new DomainException(ErrorCode.SEGREGATION_OF_DUTIES, "Initiator cannot authorise their own transfer");
        }
    }

    private void review(UUID reviewer, String note) {
        this.reviewedBy = reviewer;
        this.reviewNote = note;
    }

    private void require(Status expected) {
        if (status != expected) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Transfer is " + status + ", expected " + expected);
        }
    }

    private void moveTo(Status next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }
}
