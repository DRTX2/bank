package com.drtx.bank.ledger;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.drtx.bank.shared.tenancy.TenantOwned;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One leg of a journal entry. Positive amounts increase the account balance. Immutable (DB trigger). */
@Entity
@Table(name = "postings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Posting extends TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID journalId;
    private UUID accountId;
    private long amountMinor;
    private long balanceAfterMinor;
    private Instant createdAt;

    Posting(UUID tenantId, UUID journalId, UUID accountId, long amountMinor, long balanceAfterMinor, Instant at) {
        this.tenantId = tenantId;
        this.journalId = journalId;
        this.accountId = accountId;
        this.amountMinor = amountMinor;
        this.balanceAfterMinor = balanceAfterMinor;
        this.createdAt = at;
    }
}
