package com.drtx.bank.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.drtx.bank.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** RBAC (permissions per role), ownership (own vs any) and multi-tenant isolation. */
class AuthorizationIntegrationTest extends IntegrationTest {

    @Test
    void customersOnlySeeTheirOwnAccounts() throws Exception {
        Customer ana = newAndinaCustomer("10.00");
        Customer bob = newAndinaCustomer("10.00");

        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + ana.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ana.accountId().toString()));
        // Someone else's account looks exactly like a missing one: no enumeration.
        mvc.perform(get("/api/v1/accounts/" + bob.accountId()).header("Authorization", "Bearer " + ana.token()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts/" + bob.accountId()).header("Authorization", "Bearer " + andina("cajero")))
                .andExpect(status().isOk());
    }

    @Test
    void permissionsAreEnforcedPerEndpoint() throws Exception {
        String customer = andina("luis");
        String auditor = andina("auditor");
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit").header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit").header("Authorization", "Bearer " + auditor)).andExpect(status().isOk());
        // Auditor is read-only
        mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + auditor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "X_ROLE", "permissions", List.of("audit:read")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantAdminCanComposeCustomRolesButNeverPlatformPowers() throws Exception {
        String admin = andina("admin");
        mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "RISK_ANALYST_JR", "description", "Read-only risk",
                                "permissions", List.of("transfers:read:any", "ops:monitor")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.system").value(false));

        mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "SNEAKY", "permissions", List.of("tenants:manage")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVILEGE_ESCALATION"));
    }

    @Test
    void nobodyCanChangeTheirOwnRoles() throws Exception {
        String admin = andina("admin");
        String me = JsonPath.read(mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/v1/users/" + me + "/roles").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", List.of("TENANT_ADMIN", "BRANCH_MANAGER")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SEGREGATION_OF_DUTIES"));
    }

    @Test
    void tenantsAreIsolated() throws Exception {
        Customer andinaCustomer = newAndinaCustomer("100.00");
        String pacificoAdmin = login("PACF", "admin@pacifico.ec");
        Customer pacificoCustomer = newCustomer("PACF", "pacifico.ec", new java.math.BigDecimal("100.00"));

        // Another institution's account id is invisible, even to an admin with accounts:read:any
        mvc.perform(get("/api/v1/accounts/" + andinaCustomer.accountId()).header("Authorization", "Bearer " + pacificoAdmin))
                .andExpect(status().isNotFound());
        // And its account number cannot be paid into (ledger is per institution)
        transfer(pacificoCustomer.token(), pacificoCustomer.accountId(), andinaCustomer.accountNumber(), "10.00", key())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.isoReason").value("AC01"));
        // Listings never leak across tenants
        String list = mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + pacificoAdmin))
                .andReturn().getResponse().getContentAsString();
        List<String> emails = JsonPath.read(list, "$[*].email");
        org.assertj.core.api.Assertions.assertThat(emails).allMatch(e -> e.endsWith("@pacifico.ec"));
    }

    @Test
    void onlyPlatformOperatorsProvisionInstitutions() throws Exception {
        Map<String, Object> tenant = Map.of("code", "TEST", "name", "Test Coop", "country", "EC", "currency", "USD",
                "bic", "TESTECE1XXX", "approvalThreshold", 1000, "adminEmail", "admin@test.ec",
                "adminName", "Test Admin", "adminPassword", PASSWORD);
        mvc.perform(post("/api/v1/platform/tenants").header("Authorization", "Bearer " + andina("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(tenant)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/tenants").header("Authorization", "Bearer " + login("KNTR", "root@kuntur.dev"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(tenant)))
                .andExpect(status().isCreated());
        // The new institution is immediately usable by its own admin
        mvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + login("TEST", "admin@test.ec")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'PLATFORM_ADMIN')]").isEmpty());
    }
}
