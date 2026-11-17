package com.drtx.bank.loans;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.tenancy.TenantOwned;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A loan. Above the institution's four-eyes threshold it starts PENDING_APPROVAL and a second person decides;
 * once ACTIVE the outstanding principal only goes down, one collected installment at a time.
 */
@Entity
@Table(name = "loans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Loan extends TenantOwned {

    public enum Status { PENDING_APPROVAL, ACTIVE, PAID_OFF, REJECTED }

    @Id
    private UUID id;
    private UUID customerId;
    private UUID accountId;
    private long principalMinor;
    private int annualRateBps;
    private int termMonths;
    private long outstandingMinor;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String createdByEmail;
    private Instant createdAt;
    private String decidedByEmail;
    private Instant decidedAt;
    private String decisionNote;
    @Version
    private long version;

    Loan(UUID tenantId, UUID customerId, UUID accountId, long principalMinor, int annualRateBps, int termMonths,
         String createdByEmail, boolean fourEyes) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.accountId = accountId;
        this.principalMinor = principalMinor;
        this.annualRateBps = annualRateBps;
        this.termMonths = termMonths;
        this.outstandingMinor = principalMinor;
        this.status = fourEyes ? Status.PENDING_APPROVAL : Status.ACTIVE;
        this.createdByEmail = createdByEmail;
        this.createdAt = Instant.now();
    }

    void approve(String approverEmail, String note) {
        decide(approverEmail, note);
        status = Status.ACTIVE;
    }

    void reject(String approverEmail, String note) {
        decide(approverEmail, note);
        status = Status.REJECTED;
    }

    private void decide(String approverEmail, String note) {
        if (status != Status.PENDING_APPROVAL) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Loan is " + status);
        }
        if (createdByEmail.equalsIgnoreCase(approverEmail)) {
            throw new DomainException(ErrorCode.SEGREGATION_OF_DUTIES, "Whoever granted a loan cannot decide on it");
        }
        decidedByEmail = approverEmail;
        decidedAt = Instant.now();
        decisionNote = note;
    }

    void repay(long principalMinor) {
        outstandingMinor -= principalMinor;
        if (outstandingMinor == 0) {
            status = Status.PAID_OFF;
        }
    }
}

interface LoanRepository extends JpaRepository<Loan, UUID> {

    List<Loan> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Loan> findAllByOrderByCreatedAtDesc();
}

interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, UUID> {

    List<LoanInstallment> findByLoanIdOrderByNumber(UUID loanId);

    Optional<LoanInstallment> findFirstByLoanIdAndPaidAtIsNullOrderByNumber(UUID loanId);

    List<LoanInstallment> findByLoanIdAndPaidAtIsNullOrderByNumber(UUID loanId);

    @Query("select i.id from LoanInstallment i where i.paidAt is null and i.dueDate <= :date order by i.dueDate, i.number")
    List<UUID> dueBy(LocalDate date);
}
