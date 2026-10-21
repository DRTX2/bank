package com.drtx.bank.identity;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.drtx.bank.platform.Tenant;
import com.drtx.bank.platform.TenantRepository;
import com.drtx.bank.shared.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

    private final AuthService auth;
    private final TenantRepository tenants;

    AuthController(AuthService auth, TenantRepository tenants) {
        this.auth = auth;
        this.tenants = tenants;
    }

    record LoginRequest(@NotBlank @Size(max = 8) String tenantCode, @NotBlank @Size(max = 254) String email,
                        @NotBlank @Size(max = 128) String password) {
    }

    record RefreshRequest(@NotBlank String refreshToken) {
    }

    record TenantOption(String code, String name) {
    }

    @PostMapping("/api/v1/auth/login")
    AuthService.Session login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return auth.login(body.tenantCode(), body.email(), body.password(), request.getRemoteAddr());
    }

    @PostMapping("/api/v1/auth/refresh")
    AuthService.Session refresh(@Valid @RequestBody RefreshRequest body) {
        return auth.refresh(body.refreshToken());
    }

    @PostMapping("/api/v1/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest body) {
        auth.logout(body.refreshToken());
    }

    /** Institution picker for the login screen. In production the tenant comes from the subdomain instead. */
    @GetMapping("/api/v1/auth/tenants")
    List<TenantOption> tenants() {
        return tenants.findByStatusOrderByName(Tenant.Status.ACTIVE).stream()
                .map(t -> new TenantOption(t.getCode(), t.getName())).toList();
    }

    @GetMapping("/api/v1/me")
    AuthService.Profile me(CurrentUser user) {
        return auth.profile(user);
    }
}
