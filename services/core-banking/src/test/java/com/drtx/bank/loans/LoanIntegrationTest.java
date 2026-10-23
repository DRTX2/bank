package com.drtx.bank.loans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.drtx.bank.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class LoanIntegrationTest extends IntegrationTest {

    @Autowired
    LoanService loans;

    private UUID andinaId() {
        return jdbc.queryForObject("select id from tenants where code = 'ANDN'", UUID.class);
    }

    /** Customer notifications queued in the outbox for the borrower (delivered over WebSocket by the relay). */
    private long notices(Customer customer, String type) {
        return dba().queryForObject("""
                select count(*) from outbox_events
                where topic = 'bank.customer.notifications.v1' and aggregate_id = ? and event_type = ?""",
                Long.class, customer.userId().toString(), type);
    }

    private ResultActions decide(String token, String loanId, String decision, String otp) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("note", "Comité de crédito"));
        if (otp != null) {
            body.put("otp", otp);
        }
        return mvc.perform(post("/api/v1/loans/" + loanId + "/" + decision).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions originate(String token, UUID accountId, String principal, String rate, int months)
            throws Exception {
        return mvc.perform(post("/api/v1/loans").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("accountId", accountId, "principal", new BigDecimal(principal),
                        "annualRate", new BigDecimal(rate), "months", months))));
    }

    @Test
    void aLoanIsDisbursedThroughTheLedgerAndRepaidAtTheDayClose() throws Exception {
        Customer borrower = newAndinaCustomer("0");
        String manager = newMfaManager().token();

        // 1,200.00 at 12 % for 12 months: French installment 106.62, the first one is 12.00 interest + 94.62 principal
        String id = JsonPath.read(originate(manager, borrower.accountId(), "1200.00", "12", 12)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outstanding").value(1200.0))
                .andExpect(jsonPath("$.next.amount").value(106.62))
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(balance(borrower.token(), borrower.accountId())).isEqualByComparingTo("1200.00");
        assertThat(notices(borrower, "LoanDisbursed")).isEqualTo(1);

        String schedule = mvc.perform(get("/api/v1/loans/" + id).header("Authorization", "Bearer " + borrower.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.length()").value(12))
                .andReturn().getResponse().getContentAsString();
        List<Number> principals = JsonPath.read(schedule, "$.schedule[*].principal");
        assertThat(principals.stream().map(n -> new BigDecimal(n.toString())).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("1200.00");

        // The first installment falls due in a month; that day's close collects it.
        LocalDate firstDue = LocalDate.now(ZoneId.of("America/Guayaquil")).plusMonths(1);
        loans.collectDue(andinaId(), firstDue);

        assertThat(balance(borrower.token(), borrower.accountId())).isEqualByComparingTo("1093.38");
        assertThat(notices(borrower, "InstallmentCollected")).isEqualTo(1);
        mvc.perform(get("/api/v1/loans/" + id).header("Authorization", "Bearer " + borrower.token()))
                .andExpect(jsonPath("$.loan.outstanding").value(1105.38))
                .andExpect(jsonPath("$.schedule[0].status").value("PAID"))
                .andExpect(jsonPath("$.schedule[1].status").value("PENDING"));
        Long unbalanced = dba().queryForObject(
                "select count(*) from (select journal_id from postings group by journal_id having sum(amount_minor) <> 0) x",
                Long.class);
        assertThat(unbalanced).isZero();
    }

    @Test
    void anInstallmentThatBouncesStaysDueAndDoesNotStopTheClose() throws Exception {
        Customer borrower = newAndinaCustomer("0");
        String manager = newMfaManager().token();
        String id = JsonPath.read(originate(manager, borrower.accountId(), "500.00", "0", 2)
                .andReturn().getResponse().getContentAsString(), "$.id");
        // Freezing blocks debits, so the installment cannot be collected
        mvc.perform(patch("/api/v1/accounts/" + borrower.accountId() + "/status")
                        .header("Authorization", "Bearer " + manager).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "FROZEN"))))
                .andExpect(status().isOk());

        loans.collectDue(andinaId(), LocalDate.now(ZoneId.of("America/Guayaquil")).plusMonths(1));

        assertThat(balance(manager, borrower.accountId())).isEqualByComparingTo("500.00");
        assertThat(notices(borrower, "InstallmentMissed")).isEqualTo(1);
        mvc.perform(get("/api/v1/loans/" + id).header("Authorization", "Bearer " + manager))
                .andExpect(jsonPath("$.loan.outstanding").value(500.0))
                .andExpect(jsonPath("$.schedule[0].status").value("PENDING"));
    }

    @Test
    void aLoanAboveTheThresholdWaitsForASecondManagerWithAFreshCode() throws Exception {
        Customer borrower = newAndinaCustomer("0");
        Officer granter = newMfaManager();
        Officer approver = newMfaManager();

        // Andina's four-eyes threshold is 5,000.00: nothing is disbursed until someone else approves
        String id = JsonPath.read(originate(granter.token(), borrower.accountId(), "9000.00", "14.5", 24)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(balance(borrower.token(), borrower.accountId())).isEqualByComparingTo("0.00");
        assertThat(notices(borrower, "LoanDisbursed")).isZero();

        decide(granter.token(), id, "approve", otp(granter.mfaSecret(), 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SEGREGATION_OF_DUTIES"));
        decide(approver.token(), id, "approve", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STEP_UP_FAILED"));
        decide(approver.token(), id, "approve", otp(approver.mfaSecret(), 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.decidedBy").value(approver.email()));

        assertThat(balance(borrower.token(), borrower.accountId())).isEqualByComparingTo("9000.00");
        assertThat(notices(borrower, "LoanDisbursed")).isEqualTo(1);
        mvc.perform(get("/api/v1/loans/" + id).header("Authorization", "Bearer " + borrower.token()))
                .andExpect(jsonPath("$.schedule.length()").value(24));
    }

    @Test
    void aRejectedLoanMovesNoMoney() throws Exception {
        Customer borrower = newAndinaCustomer("0");
        String granter = newMfaManager().token();
        String id = JsonPath.read(originate(granter, borrower.accountId(), "7000.00", "18", 12)
                .andReturn().getResponse().getContentAsString(), "$.id");

        decide(newMfaManager().token(), id, "reject", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(balance(borrower.token(), borrower.accountId())).isEqualByComparingTo("0.00");
        mvc.perform(get("/api/v1/loans/" + id).header("Authorization", "Bearer " + borrower.token()))
                .andExpect(jsonPath("$.schedule.length()").value(0));
    }

    @Test
    void customersSeeOnlyTheirOwnLoansAndCannotGrantThem() throws Exception {
        Customer borrower = newAndinaCustomer("0");
        Customer other = newAndinaCustomer("0");
        String manager = newMfaManager().token();
        String id = JsonPath.read(originate(manager, borrower.accountId(), "300.00", "10", 3)
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/loans/" + id).header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/loans").header("Authorization", "Bearer " + other.token()))
                .andExpect(jsonPath("$.length()").value(0));
        originate(other.token(), other.accountId(), "300.00", "10", 3).andExpect(status().isForbidden());
    }
}
