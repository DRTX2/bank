package com.drtx.bank.ledger;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.drtx.bank.shared.tenancy.TenantOwned;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "journal_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry extends TenantOwned {

    public enum Type { TRANSFER, CASH_DEPOSIT, CASH_WITHDRAWAL, INTEREST, LOAN_DISBURSEMENT, LOAN_REPAYMENT }

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    private Type type;
    private String reference;
    private String description;
    private Instant createdAt;

    JournalEntry(UUID tenantId, Type type, String reference, String description) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.type = type;
        this.reference = reference;
        this.description = description;
        this.createdAt = Instant.now();
    }
}
