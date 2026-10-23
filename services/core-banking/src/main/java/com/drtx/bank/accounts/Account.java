package com.drtx.bank.accounts;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.tenancy.TenantOwned;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Customer or internal account. {@code balanceMinor} is a cached projection of the postings, only ever changed
 * by the ledger while holding a row lock, and double-checked by a database CHECK constraint.
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends TenantOwned {

    public enum Type { CHECKING, SAVINGS, INTERNAL }

    /** Aliases of the institution's system-managed (INTERNAL) accounts. */
    public static final String VAULT = "Cash vault";
    public static final String INTEREST_EXPENSE = "Interest expense";

    public enum Status { ACTIVE, FROZEN, CLOSED }

    @Id
    private UUID id;
    private String number;
    private UUID ownerId;
    @Enumerated(EnumType.STRING)
    private Type type;
    private String currency;
    private long balanceMinor;
    @Enumerated(EnumType.STRING)
    private Status status;
    private boolean allowNegative;
    private long dailyLimitMinor;
    private String alias;
    private Instant createdAt;
    @Version
    private long version;

    Account(UUID tenantId, String number, UUID ownerId, Type type, String currency, long dailyLimitMinor,
            String alias) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.number = number;
        this.ownerId = ownerId;
        this.type = type;
        this.currency = currency;
        this.status = Status.ACTIVE;
        // Internal settlement accounts mirror the bank's assets, so they run negative by design.
        this.allowNegative = type == Type.INTERNAL;
        this.dailyLimitMinor = dailyLimitMinor;
        this.alias = alias;
        this.createdAt = Instant.now();
    }

    /** Applies a signed movement. Called only by the ledger with the row locked. */
    public long apply(long amountMinor) {
        if (status == Status.CLOSED) {
            throw new DomainException(ErrorCode.ACCOUNT_CLOSED, "Account " + number + " is closed");
        }
        if (amountMinor < 0 && status == Status.FROZEN) {
            throw new DomainException(ErrorCode.ACCOUNT_BLOCKED, "Account " + number + " is frozen");
        }
        long next = Math.addExact(balanceMinor, amountMinor);
        if (next < 0 && !allowNegative) {
            throw new DomainException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds");
        }
        balanceMinor = next;
        return next;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    void changeStatus(Status status) {
        this.status = status;
    }
}
