package com.drtx.bank.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.drtx.bank.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void loginReturnsTokenWithGranularPermissions() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tenantCode", "ANDN", "email", "cumplimiento@andina.ec", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("COMPLIANCE_OFFICER"))
                .andReturn().getResponse().getContentAsString();
        List<String> perms = JsonPath.read(body, "$.user.permissions");
        assertThat(perms).contains("risk:review", "audit:read").doesNotContain("transfers:approve", "users:manage");
    }

    @Test
    void wrongPasswordIsRejectedAndRepeatedFailuresLockTheUser() throws Exception {
        Customer victim = newAndinaCustomer("0");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .with(r -> {
                                r.setRemoteAddr("10.0.0." + System.nanoTime() % 200);
                                return r;
                            })
                            .content(json(Map.of("tenantCode", "ANDN", "email", victim.email(), "password", "wrong-pass-1"))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tenantCode", "ANDN", "email", victim.email(), "password", PASSWORD))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("USER_LOCKED"));
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesTheWholeSession() throws Exception {
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tenantCode", "ANDN", "email", "luis@andina.ec", "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String first = JsonPath.read(login, "$.refreshToken");

        String rotated = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", first))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(rotated, "$.refreshToken");
        assertThat(second).isNotEqualTo(first);

        // Replaying the old token = theft signal: rejected, and the legitimate new token dies with it.
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", first))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", second))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
