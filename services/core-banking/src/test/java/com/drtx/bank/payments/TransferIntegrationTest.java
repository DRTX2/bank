package com.drtx.bank.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.drtx.bank.proto.risk.v1.Decision;
import com.drtx.bank.support.FakeRiskEngine;
import com.drtx.bank.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TransferIntegrationTest extends IntegrationTest {

    @Test
    void booksBalancedDoubleEntryAndMovesMoney() throws Exception {
        Customer ana = newAndinaCustomer("1000.00");
        Customer bob = newAndinaCustomer("0");

        transfer(ana.token(), ana.accountId(), bob.accountNumber(), "250.75", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.uetr").isNotEmpty())
                .andExpect(jsonPath("$.endToEndId").isNotEmpty());

        assertThat(balance(ana.token(), ana.accountId())).isEqualByComparingTo("749.25");
        assertThat(balance(bob.token(), bob.accountId())).isEqualByComparingTo("250.75");
        // Ledger invariant: every journal entry nets to zero.
        Long unbalanced = dba().queryForObject(
                "select count(*) from (select journal_id from postings group by journal_id having sum(amount_minor) <> 0) x",
                Long.class);
        assertThat(unbalanced).isZero();
    }

    @Test
    void idempotencyKeyPreventsDoublePayment() throws Exception {
        Customer ana = newAndinaCustomer("500.00");
        Customer bob = newAndinaCustomer("0");
        String key = key();

        String first = transfer(ana.token(), ana.accountId(), bob.accountNumber(), "100.00", key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String retry = transfer(ana.token(), ana.accountId(), bob.accountNumber(), "100.00", key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(retry, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(balance(bob.token(), bob.accountId())).isEqualByComparingTo("100.00");

        transfer(ana.token(), ana.accountId(), bob.accountNumber(), "999.00", key)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void insufficientFundsFailsWithIsoReasonAm04() throws Exception {
        Customer ana = newAndinaCustomer("50.00");
        Customer bob = newAndinaCustomer("0");

        transfer(ana.token(), ana.accountId(), bob.accountNumber(), "80.00", key())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("AM04"));
        assertThat(balance(ana.token(), ana.accountId())).isEqualByComparingTo("50.00");
    }

    @Test
    void invalidIbanCheckDigitsAreRejectedWithAc01() throws Exception {
        Customer ana = newAndinaCustomer("50.00");
        Customer bob = newAndinaCustomer("0");
        String broken = bob.accountNumber().substring(0, 2) + "00" + bob.accountNumber().substring(4);

        transfer(ana.token(), ana.accountId(), broken, "10.00", key())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.isoReason").value("AC01"));
    }

    @Test
    void highValueTransferNeedsFourEyesApproval() throws Exception {
        Customer ana = newAndinaCustomer("20000.00");
        Customer bob = newAndinaCustomer("0");
        Officer manager = newMfaManager();

        // Andina's approval threshold is 5,000.00
        String body = transfer(ana.token(), ana.accountId(), bob.accountNumber(), "7500.00", key())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        // Customers do not hold transfers:approve at all
        mvc.perform(post("/api/v1/transfers/" + id + "/approve").header("Authorization", "Bearer " + ana.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"self\"}"))
                .andExpect(status().isForbidden());

        // Approving is a step-up operation: no code, no approval
        mvc.perform(post("/api/v1/transfers/" + id + "/approve").header("Authorization", "Bearer " + manager.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"KYC ok\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STEP_UP_FAILED"));

        mvc.perform(post("/api/v1/transfers/" + id + "/approve").header("Authorization", "Bearer " + manager.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("note", "KYC ok", "otp", otp(manager.mfaSecret(), 1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(balance(bob.token(), bob.accountId())).isEqualByComparingTo("7500.00");
    }

    @Test
    void initiatorCannotApproveOwnTransfer() throws Exception {
        Customer ana = newAndinaCustomer("0");
        Customer bob = newAndinaCustomer("0");
        Officer officer = newMfaManager();
        String manager = officer.token();
        mvc.perform(post("/api/v1/accounts/" + ana.accountId() + "/deposits").header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 9000}")).andExpect(status().isOk());

        String body = transfer(manager, ana.accountId(), bob.accountNumber(), "6000.00", key())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/transfers/" + JsonPath.read(body, "$.id") + "/approve")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("note", "me", "otp", otp(officer.mfaSecret(), 1)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SEGREGATION_OF_DUTIES"));
    }

    @Test
    void riskReviewHoldsTransferUntilComplianceReleasesIt() throws Exception {
        Customer ana = newAndinaCustomer("900.00");
        Customer bob = newAndinaCustomer("0");
        risk.respond(r -> FakeRiskEngine.response(55, Decision.DECISION_REVIEW, "VELOCITY_10M", "NEW_BENEFICIARY"));

        String body = transfer(ana.token(), ana.accountId(), bob.accountNumber(), "300.00", key())
                .andExpect(jsonPath("$.status").value("HELD_FOR_REVIEW"))
                .andExpect(jsonPath("$.riskScore").value(55))
                .andExpect(jsonPath("$.riskSignals[0]").value("VELOCITY_10M"))
                .andReturn().getResponse().getContentAsString();
        assertThat(balance(ana.token(), ana.accountId())).isEqualByComparingTo("900.00");

        mvc.perform(post("/api/v1/transfers/" + JsonPath.read(body, "$.id") + "/review")
                        .header("Authorization", "Bearer " + andina("cumplimiento"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("release", true, "note", "Customer confirmed by phone"))))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(balance(ana.token(), ana.accountId())).isEqualByComparingTo("600.00");
    }

    @Test
    void riskDeclineRejectsWithFrad() throws Exception {
        Customer ana = newAndinaCustomer("900.00");
        Customer bob = newAndinaCustomer("0");
        risk.respond(r -> FakeRiskEngine.response(92, Decision.DECISION_DECLINE, "MULE_FAN_IN"));

        transfer(ana.token(), ana.accountId(), bob.accountNumber(), "300.00", key())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.failureCode").value("FRAD"));
    }

    @Test
    void riskEngineOutageFailsOpenForSmallAndClosedForLargeAmounts() throws Exception {
        Customer ana = newAndinaCustomer("900.00");
        Customer bob = newAndinaCustomer("0");
        risk.unavailable();

        transfer(ana.token(), ana.accountId(), bob.accountNumber(), "20.00", key())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        transfer(ana.token(), ana.accountId(), bob.accountNumber(), "200.00", key())
                .andExpect(jsonPath("$.status").value("HELD_FOR_REVIEW"))
                .andExpect(jsonPath("$.riskSignals[0]").value("RISK_ENGINE_UNAVAILABLE"));
    }

    @Test
    void concurrentTransfersNeverOverdraw() throws Exception {
        Customer ana = newAndinaCustomer("1000.00");
        Customer bob = newAndinaCustomer("0");
        List<Callable<String>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            calls.add(() -> JsonPath.read(transfer(ana.token(), ana.accountId(), bob.accountNumber(), "100.00", key())
                    .andReturn().getResponse().getContentAsString(), "$.status"));
        }
        List<String> statuses = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(10)) {
            for (Future<String> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        }
        assertThat(statuses).filteredOn("COMPLETED"::equals).hasSize(10);
        assertThat(statuses).filteredOn("FAILED"::equals).hasSize(10);
        assertThat(balance(ana.token(), ana.accountId())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance(bob.token(), bob.accountId())).isEqualByComparingTo("1000.00");
    }

    @Test
    void customerCannotDebitSomeoneElsesAccount() throws Exception {
        Customer ana = newAndinaCustomer("100.00");
        Customer eve = newAndinaCustomer("0");
        transfer(eve.token(), ana.accountId(), eve.accountNumber(), "10.00", key())
                .andExpect(status().isNotFound());
        assertThat(balance(ana.token(), ana.accountId())).isEqualByComparingTo("100.00");
    }

    @Test
    void completedTransferIsPublishedThroughTheOutbox() throws Exception {
        Customer ana = newAndinaCustomer("100.00");
        Customer bob = newAndinaCustomer("0");
        String id = JsonPath.read(transfer(ana.token(), ana.accountId(), bob.accountNumber(), "10.00", key())
                .andReturn().getResponse().getContentAsString(), "$.id");

        long deadline = System.currentTimeMillis() + 10_000;
        Integer published = 0;
        while (published == 0 && System.currentTimeMillis() < deadline) {
            published = jdbc.queryForObject("""
                    select count(*) from outbox_events
                    where aggregate_id = ? and event_type = 'TransferCompleted' and published_at is not null""",
                    Integer.class, UUID.fromString(id).toString());
            Thread.sleep(100);
        }
        assertThat(published).isEqualTo(1);
    }
}
