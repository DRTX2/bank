package com.drtx.bank.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.drtx.bank.identity.Totp;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.ObjectMapper;

/**
 * Full-stack tests against real PostgreSQL and Kafka (Testcontainers) with an in-process gRPC risk engine.
 * All subclasses share one Spring context, so containers start once per build. Tests create their own customers
 * instead of mutating shared demo data, which keeps them independent and order-free.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "Kuntur2026!";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected FakeRiskEngine risk;
    /** The app's JdbcTemplate: runs as kuntur_app, so row-level security applies. */
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private IntegrationTestConfig.DbaJdbc dbaJdbc;
    @Autowired
    protected ObjectMapper json;

    /** Owner-level access for cross-tenant invariants and for tampering as a DBA would; bypasses row-level security. */
    protected JdbcTemplate dba() {
        return dbaJdbc.jdbc();
    }

    @AfterEach
    void resetRisk() {
        risk.reset();
    }

    protected record Customer(String token, UUID userId, UUID accountId, String accountNumber, String email) {
    }

    protected String login(String tenant, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tenantCode", tenant, "email", email, "password", PASSWORD))))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Login failed for " + email + ": " + result.getResponse().getContentAsString());
        }
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    protected String andina(String user) throws Exception {
        return login("ANDN", user + "@andina.ec");
    }

    /** Onboards a brand-new customer through the teller API and funds the account with a cash deposit. */
    protected Customer newCustomer(String tenant, String domain, BigDecimal deposit) throws Exception {
        String teller = login(tenant, "cajero@" + domain);
        String email = "c" + UUID.randomUUID().toString().substring(0, 8) + "@" + domain;
        String body = mvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + teller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "fullName", "Test Customer", "nationalId", "1700000000",
                                "password", PASSWORD, "corporate", true))))
                .andReturn().getResponse().getContentAsString();
        UUID accountId = UUID.fromString(JsonPath.read(body, "$.id"));
        String number = JsonPath.read(body, "$.number");
        UUID ownerId = UUID.fromString(JsonPath.read(body, "$.ownerId"));
        if (deposit.signum() > 0) {
            mvc.perform(post("/api/v1/accounts/" + accountId + "/deposits").header("Authorization", "Bearer " + teller)
                    .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", deposit))));
        }
        return new Customer(login(tenant, email), ownerId, accountId, number, email);
    }

    /** A branch manager with MFA already active: approvals need a fresh step-up code from {@link #otp}. */
    protected record Officer(String token, String mfaSecret, String email) {
    }

    protected Officer newMfaManager() throws Exception {
        String email = "m" + UUID.randomUUID().toString().substring(0, 8) + "@andina.ec";
        mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + andina("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "fullName", "Test Manager", "password", PASSWORD,
                        "roles", List.of("BRANCH_MANAGER")))));
        String token = login("ANDN", email);
        String enrollment = mvc.perform(post("/api/v1/me/mfa/enrollment").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(enrollment, "$.secret");
        mvc.perform(post("/api/v1/me/mfa/activation").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", otp(secret, 0)))));
        return new Officer(token, secret, email);
    }

    /** Code for the current step plus {@code offset}; each accepted code burns its step, so use +1 for the next one. */
    protected static String otp(String secret, int offset) {
        return Totp.code(secret, Totp.currentStep(Instant.now()) + offset);
    }

    protected Customer newAndinaCustomer(String deposit) throws Exception {
        return newCustomer("ANDN", "andina.ec", new BigDecimal(deposit));
    }

    protected ResultActions transfer(String token, UUID source, String destination, String amount, String key)
            throws Exception {
        return mvc.perform(post("/api/v1/transfers").header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("sourceAccountId", source, "destinationAccountNumber", destination,
                        "amount", new BigDecimal(amount), "remittanceInfo", "Test"))));
    }

    protected BigDecimal balance(String token, UUID accountId) throws Exception {
        String body = mvc.perform(get("/api/v1/accounts/" + accountId).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return new BigDecimal(JsonPath.read(body, "$.balance").toString());
    }

    protected String json(Object value) {
        return json.writeValueAsString(value);
    }

    protected static String key() {
        return "test-" + UUID.randomUUID();
    }
}
