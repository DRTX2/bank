package com.drtx.bank.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.drtx.bank.shared.tenancy.TenantScope;
import com.drtx.bank.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** ADR-0009: PostgreSQL itself enforces the tenant boundary, independently of Hibernate's filter. */
class RowLevelSecurityIntegrationTest extends IntegrationTest {

    @Autowired
    TenantScope tenants;

    private UUID tenant(String code) {
        return jdbc.queryForObject("select id from tenants where code = ?", UUID.class, code);
    }

    @Test
    void theAppRoleOwnsNothingSoPoliciesCannotBeBypassed() {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("kuntur_app");
        assertThat(jdbc.queryForObject(
                "select rolsuper or rolbypassrls from pg_roles where rolname = current_user", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_tables where schemaname = 'public' and tableowner = current_user",
                Long.class)).isZero();
    }

    @Test
    void everyTenantTableHasForcedPolicies() {
        List<String> unprotected = jdbc.queryForList("""
                select c.relname
                from pg_class c
                         join pg_namespace n on n.oid = c.relnamespace
                         join pg_attribute a on a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped
                where n.nspname = 'public' and c.relkind = 'r'
                  and c.relname <> 'outbox_events' -- read cross-tenant by the relay, by design (ADR-0004)
                  and not (c.relrowsecurity and c.relforcerowsecurity)""", String.class);
        assertThat(unprotected).as("tenant tables without row-level security: add them to the migration").isEmpty();
    }

    @Test
    void aQueryThatForgetsTheTenantFilterSeesOnlyItsOwnRows() {
        UUID andina = tenant("ANDN");
        UUID pacifico = tenant("PACF");

        // No tenant bound: fail-closed, even for plain SQL that Hibernate never saw.
        assertThat(jdbc.queryForObject("select count(*) from accounts", Long.class)).isZero();

        List<UUID> seen = tenants.inTenant(andina, () -> jdbc.queryForList("select distinct tenant_id from accounts",
                UUID.class));
        assertThat(seen).containsExactly(andina);

        List<UUID> platform = tenants.asSystem(() -> jdbc.queryForList("select distinct tenant_id from accounts",
                UUID.class));
        assertThat(platform).contains(andina, pacifico);
    }

    @Test
    void aTenantCannotTouchOrPlantRowsInAnotherTenant() {
        UUID andina = tenant("ANDN");
        UUID pacifico = tenant("PACF");

        int frozen = tenants.inTenant(andina, () -> jdbc.update(
                "update accounts set status = 'FROZEN' where tenant_id = ?", pacifico));
        assertThat(frozen).isZero();

        assertThatThrownBy(() -> tenants.inTenant(andina, () -> jdbc.update("""
                insert into roles (id, tenant_id, name, system, created_at) values (?, ?, 'PLANTED', false, now())""",
                UUID.randomUUID(), pacifico))).rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void theAppRoleCannotRewriteHistoryEvenAsSystem() {
        assertThatThrownBy(() -> tenants.asSystem(() -> jdbc.update("delete from postings")))
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> tenants.asSystem(() -> jdbc.update("update audit_log set action = 'X'")))
                .rootCause().hasMessageContaining("permission denied");
    }
}
