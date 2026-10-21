package com.drtx.bank.platform;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.web.Amounts;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants")
class TenantController {

    private final TenantService service;

    TenantController(TenantService service) {
        this.service = service;
    }

    record TenantView(UUID id, String code, String name, String country, String currency, String bic,
                      Tenant.Status status, BigDecimal approvalThreshold) {
        static TenantView of(Tenant t) {
            return new TenantView(t.getId(), t.getCode(), t.getName(), t.getCountry(), t.getCurrency(), t.getBic(),
                    t.getStatus(), Amounts.fromMinor(t.getApprovalThresholdMinor()));
        }
    }

    record CreateTenant(@NotBlank @Pattern(regexp = "[A-Z]{4}") String code, @NotBlank String name,
                        @NotBlank @Pattern(regexp = "[A-Z]{2}") String country,
                        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
                        @NotBlank @Pattern(regexp = "[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?") String bic,
                        @NotNull @Positive BigDecimal approvalThreshold, @NotBlank @Email String adminEmail,
                        @NotBlank String adminName, @NotBlank String adminPassword) {
    }

    record StatusChange(@NotNull Tenant.Status status) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenants:manage')")
    List<TenantView> list() {
        return service.all().stream().map(TenantView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('tenants:manage')")
    TenantView create(CurrentUser user, @Valid @RequestBody CreateTenant body) {
        return TenantView.of(service.provision(user, new TenantService.ProvisionCommand(body.code(), body.name(),
                body.country(), body.currency(), body.bic(), Amounts.toMinor(body.approvalThreshold()),
                body.adminEmail(), body.adminName(), body.adminPassword())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('tenants:manage')")
    TenantView changeStatus(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody StatusChange body) {
        return TenantView.of(service.changeStatus(user, id, body.status()));
    }
}
