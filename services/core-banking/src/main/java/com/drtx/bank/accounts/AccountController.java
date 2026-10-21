package com.drtx.bank.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.drtx.bank.ledger.LedgerService;
import com.drtx.bank.ledger.MovementView;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.web.Amounts;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AccountController {

    private final AccountService accounts;
    private final LedgerService ledger;

    AccountController(AccountService accounts, LedgerService ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    record OpenAccount(@NotNull UUID ownerId, @NotNull Account.Type type, @Size(max = 60) String alias) {
    }

    record CashOperation(@NotNull @Positive BigDecimal amount, @Size(max = 64) String reference) {
    }

    record StatusChange(@NotNull Account.Status status) {
    }

    record Onboarding(@NotBlank @Email String email, @NotBlank @Size(max = 120) String fullName,
                      @NotBlank @Size(max = 20) String nationalId, @NotBlank String password, boolean corporate) {
    }

    record Movement(Long id, String type, String reference, String description, BigDecimal amount,
                    BigDecimal balanceAfter, Instant bookedAt) {
        static Movement of(MovementView m) {
            return new Movement(m.postingId(), m.type().name(), m.reference(), m.description(),
                    BigDecimal.valueOf(m.amountMinor()).movePointLeft(2),
                    BigDecimal.valueOf(m.balanceAfterMinor()).movePointLeft(2), m.bookedAt());
        }
    }

    @GetMapping("/api/v1/accounts")
    @PreAuthorize("hasAnyAuthority('accounts:read:own', 'accounts:read:any')")
    List<AccountView> list(CurrentUser user) {
        return accounts.visibleTo(user);
    }

    @GetMapping("/api/v1/accounts/{id}")
    @PreAuthorize("hasAnyAuthority('accounts:read:own', 'accounts:read:any')")
    AccountView get(CurrentUser user, @PathVariable UUID id) {
        return accounts.view(user, id);
    }

    @GetMapping("/api/v1/accounts/{id}/movements")
    @PreAuthorize("hasAnyAuthority('accounts:read:own', 'accounts:read:any')")
    List<Movement> movements(CurrentUser user, @PathVariable UUID id,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        accounts.readable(user, id);
        Instant start = (from == null ? LocalDate.now().minusDays(30) : from).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return ledger.movements(id, start, end).stream().map(Movement::of).toList();
    }

    @GetMapping("/api/v1/accounts/lookup")
    @PreAuthorize("hasAnyAuthority('transfers:create:own', 'transfers:create:any')")
    AccountService.Lookup lookup(@RequestParam String number) {
        return accounts.lookup(number);
    }

    @PostMapping("/api/v1/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('accounts:open')")
    AccountView open(CurrentUser user, @Valid @RequestBody OpenAccount body) {
        return new AccountView(accounts.open(user, body.ownerId(), body.type(), body.alias()), null);
    }

    @PatchMapping("/api/v1/accounts/{id}/status")
    @PreAuthorize("hasAuthority('accounts:freeze')")
    AccountView changeStatus(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody StatusChange body) {
        return new AccountView(accounts.changeStatus(user, id, body.status()), null);
    }

    @PostMapping("/api/v1/accounts/{id}/deposits")
    @PreAuthorize("hasAuthority('cash:deposit')")
    AccountView deposit(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody CashOperation body) {
        accounts.cash(user, id, Amounts.toMinor(body.amount()), true, body.reference());
        return accounts.view(user, id);
    }

    @PostMapping("/api/v1/accounts/{id}/withdrawals")
    @PreAuthorize("hasAuthority('cash:withdraw')")
    AccountView withdraw(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody CashOperation body) {
        accounts.cash(user, id, Amounts.toMinor(body.amount()), false, body.reference());
        return accounts.view(user, id);
    }

    @PostMapping("/api/v1/customers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customers:onboard')")
    AccountView onboard(CurrentUser user, @Valid @RequestBody Onboarding body) {
        Account account = accounts.onboardCustomer(user, body.email(), body.fullName(), body.nationalId(),
                body.password(), body.corporate());
        return new AccountView(account, body.fullName());
    }
}
