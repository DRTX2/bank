package com.drtx.bank.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.drtx.bank.shared.tenancy.TenantOwned;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Opaque refresh token, stored hashed. Tokens of one login share a family so reuse revokes the whole chain. */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RefreshToken extends TenantOwned {

    @Id
    private UUID id;
    private UUID userId;
    private String tokenHash;
    private UUID familyId;
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant createdAt;

    RefreshToken(UUID tenantId, UUID userId, String tokenHash, UUID familyId, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
