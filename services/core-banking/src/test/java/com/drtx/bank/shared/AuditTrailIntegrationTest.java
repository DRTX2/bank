package com.drtx.bank.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.drtx.bank.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuditTrailIntegrationTest extends IntegrationTest {

    @Test
    void ledgerAndAuditAreAppendOnlyAtTheDatabaseLevel() {
        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'X' where id = (select min(id) from audit_log)"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from postings where id = (select min(id) from postings)"))
                .hasMessageContaining("append-only");
    }

    @Test
    void hashChainDetectsTamperingByAPrivilegedInsider() throws Exception {
        // Dedicated institution so tampering does not affect other tests.
        String code = "Z" + UUID.randomUUID().toString().substring(0, 3).toUpperCase().replaceAll("[^A-Z]", "Q");
        mvc.perform(post("/api/v1/platform/tenants").header("Authorization", "Bearer " + login("KNTR", "root@kuntur.dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", "Audit Coop", "country", "EC", "currency", "USD",
                                "bic", code + "ECE1XXX", "approvalThreshold", 1000, "adminEmail", "admin@audit.ec",
                                "adminName", "Audit Admin", "adminPassword", PASSWORD))))
                .andExpect(status().isCreated());
        String admin = login(code, "admin@audit.ec");
        mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "ROLE_A", "permissions", java.util.List.of("audit:read")))));

        mvc.perform(get("/api/v1/audit/verify").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.valid").value(true));

        UUID tenantId = jdbc.queryForObject("select id from tenants where code = ?", UUID.class, code);
        Long victim = jdbc.queryForObject("select min(id) from audit_log where tenant_id = ?", Long.class, tenantId);
        // A DBA bypasses the trigger and rewrites history...
        jdbc.execute("alter table audit_log disable trigger audit_append_only");
        try {
            jdbc.update("update audit_log set details = 'nothing to see here' where id = ?", victim);
        } finally {
            jdbc.execute("alter table audit_log enable trigger audit_append_only");
        }
        // ...and the chain exposes exactly where.
        mvc.perform(get("/api/v1/audit/verify").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.firstBrokenId").value(victim));
        assertThat(victim).isNotNull();
    }
}
