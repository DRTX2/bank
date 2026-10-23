package com.drtx.bank.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.drtx.bank.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DayCloseIntegrationTest extends IntegrationTest {

    @Test
    void closingTheDayCreditsOneDayOfInterestExactlyOnce() throws Exception {
        // A dedicated institution: the close touches every savings account, so it must not see other tests' data.
        String code = "D" + UUID.randomUUID().toString().substring(0, 3).toUpperCase().replaceAll("[^A-Z]", "Q");
        String domain = code.toLowerCase() + ".ec";
        mvc.perform(post("/api/v1/platform/tenants").header("Authorization", "Bearer " + login("KNTR", "root@kuntur.dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", "Interest Coop", "country", "EC", "currency", "USD",
                                "bic", code + "ECE1XXX", "approvalThreshold", 1000, "adminEmail", "admin@" + domain,
                                "adminName", "Interest Admin", "adminPassword", PASSWORD))))
                .andExpect(status().isCreated());
        String admin = login(code, "admin@" + domain);
        mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "cajero@" + domain, "fullName", "Cajero", "password", PASSWORD,
                                "roles", List.of("TELLER")))))
                .andExpect(status().isCreated());
        Customer saver = newCustomer(code, domain, new BigDecimal("10000.00"));
        newCustomer(code, domain, BigDecimal.ZERO);

        // 10,000.00 at the default 3.00 % a year, actual/365: 0.8219 -> 0.82; the empty account earns nothing.
        mvc.perform(post("/api/v1/ops/day-closes").header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountsCredited").value(1))
                .andExpect(jsonPath("$.interest").value(0.82))
                .andExpect(jsonPath("$.closedBy").value("admin@" + domain));
        assertThat(balance(saver.token(), saver.accountId())).isEqualByComparingTo("10000.82");
        assertThat(dba().queryForObject(
                "select count(*) from outbox_events where event_type = 'InterestCredited' and aggregate_id = ?",
                Long.class, saver.userId().toString())).isEqualTo(1);

        mvc.perform(post("/api/v1/ops/day-closes").header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAY_ALREADY_CLOSED"));
        assertThat(balance(saver.token(), saver.accountId())).isEqualByComparingTo("10000.82");

        mvc.perform(get("/api/v1/ops/day-closes").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.todayClosed").value(true))
                .andExpect(jsonPath("$.annualRate").value(3.0))
                .andExpect(jsonPath("$.closes.length()").value(1));

        // The interest expense account carries the other side: every journal entry still nets to zero.
        Long unbalanced = dba().queryForObject(
                "select count(*) from (select journal_id from postings group by journal_id having sum(amount_minor) <> 0) x",
                Long.class);
        assertThat(unbalanced).isZero();
        mvc.perform(get("/api/v1/audit/verify").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void onlyOperationsRolesCanCloseTheDay() throws Exception {
        mvc.perform(post("/api/v1/ops/day-closes").header("Authorization", "Bearer " + andina("cajero")))
                .andExpect(status().isForbidden());
    }
}
