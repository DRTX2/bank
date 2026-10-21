package com.drtx.bank.shared.tenancy;

import java.util.UUID;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TenancyConfig {

    @Bean
    HibernatePropertiesCustomizer tenantResolverCustomizer() {
        return props -> props.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new Resolver());
    }

    /** Hibernate reads the tenant once, when a session opens (i.e. at transaction start). */
    static final class Resolver implements CurrentTenantIdentifierResolver<UUID> {

        @Override
        public UUID resolveCurrentTenantIdentifier() {
            return TenantContext.current();
        }

        @Override
        public boolean validateExistingCurrentSessions() {
            return false;
        }

        @Override
        public boolean isRoot(UUID tenantId) {
            return TenantContext.SYSTEM.equals(tenantId);
        }
    }
}
