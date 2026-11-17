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
import jakarta.validation.constraints.Size;

import com.drtx.bank.identity.MfaService;
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
    private final MfaService mfa;

    LoanController(LoanService loans, MfaService mfa) {
        this.loans = loans;
        this.mfa = mfa;
    }

    /** {@code otp} is the approver's step-up code; rejecting moves no money and does not need one. */
    record Decision(@Size(max = 255) String note, @Size(max = 10) String otp) {
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

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('loans:originate')")
    LoanService.LoanView approve(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody Decision body) {
        mfa.requireStepUp(user, body.otp());
        return loans.approve(user, id, body.note());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('loans:originate')")
    LoanService.LoanView reject(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody Decision body) {
        return loans.reject(user, id, body.note());
    }

    @GetMapping("/{id}/payoff")
    @PreAuthorize("hasAuthority('loans:repay:own')")
    LoanService.Payoff payoffQuote(CurrentUser user, @PathVariable UUID id) {
        return loans.payoffQuote(user, id);
    }

    @PostMapping("/{id}/payoff")
    @PreAuthorize("hasAuthority('loans:repay:own')")
    LoanService.LoanView payoff(CurrentUser user, @PathVariable UUID id) {
        return loans.payoff(user, id);
    }
}
