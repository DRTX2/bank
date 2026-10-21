package com.drtx.bank.shared.tenancy;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tenant bound to the current thread. Fail-closed: when nothing is bound, Hibernate filters on a
 * tenant id that matches no row. Cross-tenant access must opt in explicitly through {@link #SYSTEM}.
 */
public final class TenantContext {

    /** Hibernate "root" tenant: no filtering. Only for platform jobs (outbox relay, provisioning, seeding). */
    public static final UUID SYSTEM = new UUID(0, 0);
    /** Bound when no tenant is known; matches nothing. */
    public static final UUID NONE = new UUID(0, 1);

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID current() {
        UUID tenant = CURRENT.get();
        return tenant == null ? NONE : tenant;
    }

    /** The real tenant of the caller; throws for system or unbound contexts. */
    public static UUID require() {
        UUID tenant = CURRENT.get();
        if (tenant == null || SYSTEM.equals(tenant)) {
            throw new IllegalStateException("No tenant bound to the current thread");
        }
        return tenant;
    }

    public static void set(UUID tenant) {
        CURRENT.set(tenant);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T callAs(UUID tenant, Supplier<T> work) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenant);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(UUID tenant, Runnable work) {
        callAs(tenant, () -> {
            work.run();
            return null;
        });
    }
}
