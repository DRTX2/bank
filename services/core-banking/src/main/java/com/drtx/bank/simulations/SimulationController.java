package com.drtx.bank.simulations;

import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stateless financial calculators. Bounds keep inputs realistic and computation cheap. */
@RestController
@RequestMapping("/api/v1/simulations")
@PreAuthorize("hasAuthority('simulations:use')")
class SimulationController {

    record LoanRequest(@NotNull @DecimalMin("100") @DecimalMax("1000000") BigDecimal principal,
                       @NotNull @DecimalMin("0") @DecimalMax("60") BigDecimal annualRatePct,
                       @Min(1) @Max(360) int months, @NotNull LoanSimulator.Method method,
                       @DecimalMin("0") @DecimalMax("2") BigDecimal monthlyInsurancePct) {
    }

    record DepositRequest(@NotNull @DecimalMin("100") @DecimalMax("10000000") BigDecimal amount,
                          @NotNull @DecimalMin("0") @DecimalMax("30") BigDecimal annualRatePct,
                          @Min(30) @Max(1825) int days,
                          @NotNull @DecimalMin("0") @DecimalMax("50") BigDecimal withholdingTaxPct) {
    }

    record GoalRequest(@NotNull @DecimalMin("1") @DecimalMax("10000000") BigDecimal target,
                       @NotNull @PositiveOrZero BigDecimal initial, @Min(1) @Max(600) int months,
                       @NotNull @DecimalMin("0") @DecimalMax("30") BigDecimal annualRatePct) {
    }

    record CapacityRequest(@NotNull @DecimalMin("1") BigDecimal monthlyIncome, @NotNull @PositiveOrZero BigDecimal monthlyExpenses,
                           @NotNull @PositiveOrZero BigDecimal existingDebtPayments,
                           @NotNull @DecimalMin("0") @DecimalMax("60") BigDecimal annualRatePct,
                           @Min(1) @Max(360) int months,
                           @DecimalMin("5") @DecimalMax("60") BigDecimal maxDebtToIncomePct) {
    }

    @PostMapping("/loan")
    LoanSimulator.Result loan(@Valid @RequestBody LoanRequest r) {
        return LoanSimulator.simulate(new LoanSimulator.Input(r.principal(), r.annualRatePct(), r.months(), r.method(),
                r.monthlyInsurancePct()));
    }

    @PostMapping("/term-deposit")
    SavingsSimulators.DepositResult termDeposit(@Valid @RequestBody DepositRequest r) {
        return SavingsSimulators.termDeposit(new SavingsSimulators.DepositInput(r.amount(), r.annualRatePct(), r.days(),
                r.withholdingTaxPct()));
    }

    @PostMapping("/savings-goal")
    SavingsSimulators.GoalResult savingsGoal(@Valid @RequestBody GoalRequest r) {
        return SavingsSimulators.savingsGoal(new SavingsSimulators.GoalInput(r.target(), r.initial(), r.months(),
                r.annualRatePct()));
    }

    @PostMapping("/borrowing-capacity")
    SavingsSimulators.CapacityResult capacity(@Valid @RequestBody CapacityRequest r) {
        return SavingsSimulators.borrowingCapacity(new SavingsSimulators.CapacityInput(r.monthlyIncome(),
                r.monthlyExpenses(), r.existingDebtPayments(), r.annualRatePct(), r.months(), r.maxDebtToIncomePct()));
    }
}
