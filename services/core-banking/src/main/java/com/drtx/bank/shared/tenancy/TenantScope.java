package com.drtx.bank.shared.tenancy;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs work in a fresh transaction bound to another tenant. A new transaction is required because
 * Hibernate fixes the tenant of a session when it opens.
 */
@Component
public class TenantScope {

    private final TransactionTemplate requiresNew;

    TenantScope(PlatformTransactionManager txManager) {
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T inTenant(UUID tenantId, Supplier<T> work) {
        return TenantContext.callAs(tenantId, () -> requiresNew.execute(status -> work.get()));
    }

    public <T> T asSystem(Supplier<T> work) {
        return inTenant(TenantContext.SYSTEM, work);
    }
}
