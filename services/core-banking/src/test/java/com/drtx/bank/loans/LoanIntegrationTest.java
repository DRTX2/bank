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
