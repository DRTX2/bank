package com.drtx.bank.loans;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.drtx.bank.shared.tenancy.TenantOwned;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "loan_installments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanInstallment extends TenantOwned {

    @Id
    private UUID id;
    private UUID loanId;
    private int number;
    private LocalDate dueDate;
    private long principalMinor;
    private long interestMinor;
    private Instant paidAt;
    private UUID journalId;
    private boolean prepaid;
    @Version
    private long version;

    LoanInstallment(UUID tenantId, UUID loanId, int number, LocalDate dueDate, long principalMinor, long interestMinor) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.loanId = loanId;
        this.number = number;
        this.dueDate = dueDate;
        this.principalMinor = principalMinor;
        this.interestMinor = interestMinor;
    }

    long amountMinor() {
        return principalMinor + interestMinor;
    }

    void prepaid(UUID payoffJournalId) {
        paid(payoffJournalId);
        this.prepaid = true;
    }

    void paid(UUID journalId) {
        this.paidAt = Instant.now();
        this.journalId = journalId;
    }
}
