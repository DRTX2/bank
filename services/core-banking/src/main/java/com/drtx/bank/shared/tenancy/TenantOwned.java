package com.drtx.bank.shared.tenancy;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.TenantId;

/** Base for tenant-scoped entities: Hibernate stamps and filters {@code tenant_id} automatically. */
@MappedSuperclass
public abstract class TenantOwned {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    protected UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }
}
