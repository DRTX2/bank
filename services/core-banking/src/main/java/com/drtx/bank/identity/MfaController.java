package com.drtx.bank.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.drtx.bank.shared.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Self-service second factor: any signed-in user manages their own MFA, nobody else's. */
@RestController
@RequestMapping("/api/v1/me/mfa")
@PreAuthorize("isAuthenticated()")
class MfaController {

    private final MfaService mfa;

    MfaController(MfaService mfa) {
        this.mfa = mfa;
    }

    record Code(@NotBlank @Size(max = 10) String code) {
    }

    @PostMapping("/enrollment")
    MfaService.Enrollment enroll(CurrentUser user) {
        return mfa.enroll(user);
    }

    @PostMapping("/activation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void activate(CurrentUser user, @Valid @RequestBody Code body) {
        mfa.activate(user, body.code());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disable(CurrentUser user, @Valid @RequestBody Code body) {
        mfa.disable(user, body.code());
    }
}
