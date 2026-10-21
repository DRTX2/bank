package com.drtx.bank.simulations;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class SimulatorsTest {

    @Test
    void frenchAmortizationMatchesTheAnnuityFormulaAndClosesAtZero() {
        // 10,000 at 12% nominal over 12 months -> 888.49 per month (textbook value)
        var result = LoanSimulator.simulate(new LoanSimulator.Input(new BigDecimal("10000"), new BigDecimal("12"), 12,
                LoanSimulator.Method.FRENCH, BigDecimal.ZERO));
        assertThat(result.firstInstallment()).isEqualByComparingTo("888.49");
        assertThat(result.schedule()).hasSize(12);
        assertThat(result.schedule().getLast().balance()).isEqualByComparingTo("0");
        assertThat(result.totalInterest()).isBetween(new BigDecimal("661.80"), new BigDecimal("661.90"));
        assertThat(result.effectiveAnnualRatePct()).isEqualByComparingTo("12.68");
    }

    @Test
    void germanAmortizationHasConstantPrincipalAndDecreasingInstallments() {
        var result = LoanSimulator.simulate(new LoanSimulator.Input(new BigDecimal("12000"), new BigDecimal("12"), 12,
                LoanSimulator.Method.GERMAN, new BigDecimal("0.05")));
        assertThat(result.schedule()).allSatisfy(r -> assertThat(r.principal()).isEqualByComparingTo("1000"));
        assertThat(result.firstInstallment()).isGreaterThan(result.lastInstallment());
        assertThat(result.firstInstallment()).isEqualByComparingTo("1126.00"); // 1000 + 120 interest + 6 insurance
        assertThat(result.schedule().getLast().balance()).isEqualByComparingTo("0");
    }

    @Test
    void termDepositUsesThe360DayYearAndWithholdsTax() {
        var r = SavingsSimulators.termDeposit(new SavingsSimulators.DepositInput(new BigDecimal("10000"),
                new BigDecimal("7.2"), 180, new BigDecimal("2")));
        assertThat(r.grossInterest()).isEqualByComparingTo("360.00");
        assertThat(r.withholdingTax()).isEqualByComparingTo("7.20");
        assertThat(r.maturityAmount()).isEqualByComparingTo("10352.80");
    }

    @Test
    void savingsGoalProjectionReachesTheTarget() {
        var r = SavingsSimulators.savingsGoal(new SavingsSimulators.GoalInput(new BigDecimal("5000"),
                new BigDecimal("500"), 24, new BigDecimal("5")));
        assertThat(r.projection()).hasSize(24);
        assertThat(r.projection().getLast().balance()).isGreaterThanOrEqualTo(new BigDecimal("5000"));
        assertThat(r.projection().getLast().balance()).isLessThan(new BigDecimal("5025"));
    }

    @Test
    void borrowingCapacityIsCappedByDebtToIncomeAndDisposableIncome() {
        var r = SavingsSimulators.borrowingCapacity(new SavingsSimulators.CapacityInput(new BigDecimal("2000"),
                new BigDecimal("900"), new BigDecimal("200"), new BigDecimal("15"), 36, new BigDecimal("35")));
        // DTI cap: 2000*35% - 200 = 500; disposable: (2000-900-200)*0.7 = 630 -> 500
        assertThat(r.maxInstallment()).isEqualByComparingTo("500.00");
        assertThat(r.maxLoanAmount()).isBetween(new BigDecimal("14400"), new BigDecimal("14500"));
        assertThat(r.rating()).isEqualTo("STRONG"); // existing debt is only 10% of income
    }
}
