package com.drtx.bank.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.drtx.bank.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class MfaIntegrationTest extends IntegrationTest {

    @Test
    void loginAsksForTheSecondFactorAndEachCodeWorksOnce() throws Exception {
        Officer manager = newMfaManager();

        loginWith(manager.email(), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
        // Five steps ahead is outside the drift window, so this is a wrong code
        loginWith(manager.email(), otp(manager.mfaSecret(), 5))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_INVALID"));

        String code = otp(manager.mfaSecret(), 1);
        loginWith(manager.email(), code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mfaEnabled").value(true));
        loginWith(manager.email(), code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_INVALID"));
    }

    @Test
    void theSeedIsSealedInTheDatabase() throws Exception {
        Officer manager = newMfaManager();

        String stored = dba().queryForObject("select mfa_secret from users where email = ?", String.class,
                manager.email());
        assertThat(stored).startsWith("v1:").doesNotContain(manager.mfaSecret());
    }

    @Test
    void approvingWithoutMfaEnrolledIsRefused() throws Exception {
        String email = "m" + UUID.randomUUID().toString().substring(0, 8) + "@andina.ec";
        mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + andina("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "fullName", "No MFA", "password", PASSWORD,
                                "roles", List.of("BRANCH_MANAGER")))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/transfers/" + UUID.randomUUID() + "/approve")
                        .header("Authorization", "Bearer " + login("ANDN", email))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("otp", "123456"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MFA_NOT_ENROLLED"));
    }

    @Test
    void activationNeedsAValidCode() throws Exception {
        Customer luis = newAndinaCustomer("0");
        mvc.perform(post("/api/v1/me/mfa/enrollment").header("Authorization", "Bearer " + luis.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/Kuntur%3A")));
        mvc.perform(post("/api/v1/me/mfa/activation").header("Authorization", "Bearer " + luis.token())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", "000000"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_INVALID"));
    }

    private ResultActions loginWith(String email, String otp) throws Exception {
        Map<String, String> body = new HashMap<>(Map.of("tenantCode", "ANDN", "email", email, "password", PASSWORD));
        if (otp != null) {
            body.put("otp", otp);
        }
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }
}
