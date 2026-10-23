package com.drtx.bank.loans;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.web.Amounts;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
class LoanController {

    private final LoanService loans;

    LoanController(LoanService loans) {
        this.loans = loans;
    }

    record Originate(@NotNull UUID accountId,
                     @NotNull @DecimalMin("100") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2) BigDecimal principal,
                     @NotNull @DecimalMin("0") @DecimalMax("60") @Digits(integer = 2, fraction = 2) BigDecimal annualRate,
                     @Min(1) @Max(360) int months) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('loans:originate')")
    LoanService.LoanView originate(CurrentUser user, @Valid @RequestBody Originate body) {
        return loans.originate(user, body.accountId(), Amounts.toMinor(body.principal()),
                body.annualRate().movePointRight(2).intValueExact(), body.months());
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('loans:read:own', 'loans:read:any')")
    List<LoanService.LoanView> list(CurrentUser user) {
        return loans.list(user);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('loans:read:own', 'loans:read:any')")
    LoanService.LoanDetail detail(CurrentUser user, @PathVariable UUID id) {
        return loans.detail(user, id);
    }
}
