package com.drtx.bank.simulations;

import static com.drtx.bank.simulations.LoanSimulator.MC;
import static com.drtx.bank.simulations.LoanSimulator.money;
import static com.drtx.bank.simulations.LoanSimulator.monthlyRate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Term deposits, savings goals and borrowing capacity: the "what if" tools customers use before committing. */
public final class SavingsSimulators {

    private SavingsSimulators() {
    }

    // ------------------------------------------------------------ term deposit (plazo fijo / CDT)

    public record DepositInput(BigDecimal amount, BigDecimal annualRatePct, int days, BigDecimal withholdingTaxPct) {
    }

    public record DepositResult(BigDecimal grossInterest, BigDecimal withholdingTax, BigDecimal netInterest,
                                BigDecimal maturityAmount, BigDecimal effectiveAnnualYieldPct) {
    }

    /** Simple interest on a 360-day commercial year, as used by Ecuadorian and most LATAM banks. */
    public static DepositResult termDeposit(DepositInput in) {
        BigDecimal gross = money(in.amount().multiply(in.annualRatePct(), MC).multiply(BigDecimal.valueOf(in.days()), MC)
                .divide(BigDecimal.valueOf(36_000), MC));
        BigDecimal tax = money(gross.multiply(in.withholdingTaxPct(), MC).divide(BigDecimal.valueOf(100), MC));
        BigDecimal net = gross.subtract(tax);
        BigDecimal periodYield = net.divide(in.amount(), MC);
        double annualized = Math.pow(1 + periodYield.doubleValue(), 365.0 / in.days()) - 1;
        return new DepositResult(gross, tax, net, in.amount().add(net),
                BigDecimal.valueOf(annualized * 100).setScale(2, RoundingMode.HALF_UP));
    }

    // ------------------------------------------------------------ savings goal

    public record GoalInput(BigDecimal target, BigDecimal initial, int months, BigDecimal annualRatePct) {
    }

    public record GoalPoint(int month, BigDecimal contributed, BigDecimal interestEarned, BigDecimal balance) {
    }

    public record GoalResult(BigDecimal monthlyDeposit, BigDecimal totalContributed, BigDecimal totalInterest,
                             List<GoalPoint> projection) {
    }

    /** Monthly deposit d so that initial·(1+r)^n + d·((1+r)^n − 1)/r = target. */
    public static GoalResult savingsGoal(GoalInput in) {
        BigDecimal r = monthlyRate(in.annualRatePct());
        BigDecimal growth = BigDecimal.ONE.add(r).pow(in.months(), MC);
        BigDecimal remaining = in.target().subtract(in.initial().multiply(growth, MC));
        BigDecimal deposit = remaining.signum() <= 0 ? BigDecimal.ZERO
                : r.signum() == 0 ? remaining.divide(BigDecimal.valueOf(in.months()), MC)
                : remaining.multiply(r, MC).divide(growth.subtract(BigDecimal.ONE), MC);
        deposit = deposit.setScale(2, RoundingMode.CEILING);

        List<GoalPoint> points = new ArrayList<>();
        BigDecimal balance = in.initial();
        BigDecimal contributed = in.initial();
        for (int m = 1; m <= in.months(); m++) {
            balance = money(balance.add(balance.multiply(r, MC))).add(deposit);
            contributed = contributed.add(deposit);
            points.add(new GoalPoint(m, contributed, balance.subtract(contributed), balance));
        }
        return new GoalResult(deposit, contributed, balance.subtract(contributed), points);
    }

    // ------------------------------------------------------------ borrowing capacity

    public record CapacityInput(BigDecimal monthlyIncome, BigDecimal monthlyExpenses, BigDecimal existingDebtPayments,
                                BigDecimal annualRatePct, int months, BigDecimal maxDebtToIncomePct) {
    }

    public record CapacityResult(BigDecimal maxInstallment, BigDecimal maxLoanAmount, BigDecimal debtToIncomePct,
                                 String rating) {
    }

    /**
     * Max installment = min(income·DTI − existing debt, disposable income · 70%); max loan = present value of that
     * annuity. The rating is a rough affordability signal, not a credit decision.
     */
    public static CapacityResult borrowingCapacity(CapacityInput in) {
        BigDecimal dti = in.maxDebtToIncomePct() == null ? BigDecimal.valueOf(35) : in.maxDebtToIncomePct();
        BigDecimal byDti = in.monthlyIncome().multiply(dti, MC).divide(BigDecimal.valueOf(100), MC)
                .subtract(in.existingDebtPayments());
        BigDecimal disposable = in.monthlyIncome().subtract(in.monthlyExpenses()).subtract(in.existingDebtPayments())
                .multiply(BigDecimal.valueOf(0.7), MC);
        BigDecimal installment = money(byDti.min(disposable).max(BigDecimal.ZERO));
        BigDecimal r = monthlyRate(in.annualRatePct());
        BigDecimal loan = r.signum() == 0 ? installment.multiply(BigDecimal.valueOf(in.months()))
                : installment.multiply(BigDecimal.ONE.subtract(BigDecimal.ONE.add(r).pow(-in.months(), MC)), MC).divide(r, MC);
        BigDecimal currentDti = in.monthlyIncome().signum() == 0 ? BigDecimal.valueOf(100)
                : in.existingDebtPayments().multiply(BigDecimal.valueOf(100)).divide(in.monthlyIncome(), 2, RoundingMode.HALF_UP);
        String rating = installment.signum() == 0 ? "NO_CAPACITY"
                : currentDti.compareTo(BigDecimal.valueOf(20)) < 0 ? "STRONG"
                : currentDti.compareTo(dti) < 0 ? "MODERATE" : "TIGHT";
        return new CapacityResult(installment, money(loan), currentDti, rating);
    }
}
