package com.drtx.bank.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.drtx.bank.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CashIntegrationTest extends IntegrationTest {

    @Test
    void concurrentTellerDepositsAllSucceed() throws Exception {
        // Every deposit also books against the institution's single cash vault: the hottest row in the system
        Customer ana = newAndinaCustomer("0");
        String teller = andina("cajero");
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            calls.add(() -> mvc.perform(post("/api/v1/accounts/" + ana.accountId() + "/deposits")
                            .header("Authorization", "Bearer " + teller)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 10.00}"))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(6)) {
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        }
        assertThat(statuses).containsOnly(200);
        assertThat(balance(ana.token(), ana.accountId())).isEqualByComparingTo("120.00");
    }
}
