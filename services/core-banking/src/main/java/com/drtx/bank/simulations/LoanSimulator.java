package com.drtx.bank.simulations;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Loan amortization. FRENCH = constant installment (most consumer loans in LATAM); GERMAN = constant principal,
 * decreasing installment. Includes credit-life insurance (seguro de desgravamen) on the outstanding balance.
 * Rounding is applied per period and the last installment absorbs the residue, so the balance closes at exactly 0.
 */
public final class LoanSimulator {

    public enum Method { FRENCH, GERMAN }

    public record Input(BigDecimal principal, BigDecimal annualRatePct, int months, Method method,
                        BigDecimal monthlyInsurancePct) {
    }

    public record Row(int period, BigDecimal installment, BigDecimal principal, BigDecimal interest,
                      BigDecimal insurance, BigDecimal balance) {
    }

    public record Result(BigDecimal firstInstallment, BigDecimal lastInstallment, BigDecimal totalInterest,
                         BigDecimal totalInsurance, BigDecimal totalPaid, BigDecimal effectiveAnnualRatePct,
                         List<Row> schedule) {
    }

    static final MathContext MC = MathContext.DECIMAL128;

    private LoanSimulator() {
    }

    public static Result simulate(Input in) {
        BigDecimal r = monthlyRate(in.annualRatePct());
        BigDecimal ins = in.monthlyInsurancePct() == null ? BigDecimal.ZERO
                : in.monthlyInsurancePct().divide(BigDecimal.valueOf(100), MC);
        BigDecimal balance = in.principal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal frenchInstallment = r.signum() == 0
                ? in.principal().divide(BigDecimal.valueOf(in.months()), MC)
                : in.principal().multiply(r, MC).divide(BigDecimal.ONE.subtract(BigDecimal.ONE.add(r).pow(-in.months(), MC), MC), MC);
        BigDecimal germanPrincipal = in.principal().divide(BigDecimal.valueOf(in.months()), MC);

        List<Row> rows = new ArrayList<>(in.months());
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalInsurance = BigDecimal.ZERO;
        for (int k = 1; k <= in.months(); k++) {
            BigDecimal interest = money(balance.multiply(r, MC));
            BigDecimal insurance = money(balance.multiply(ins, MC));
            BigDecimal principalPart = k == in.months() ? balance
                    : money(in.method() == Method.FRENCH ? frenchInstallment.subtract(interest) : germanPrincipal);
            balance = balance.subtract(principalPart);
            BigDecimal installment = principalPart.add(interest);
            rows.add(new Row(k, installment.add(insurance), principalPart, interest, insurance, balance));
            totalInterest = totalInterest.add(interest);
            totalInsurance = totalInsurance.add(insurance);
        }
        BigDecimal ear = BigDecimal.ONE.add(r).pow(12, MC).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100));
        return new Result(rows.getFirst().installment(), rows.getLast().installment(), totalInterest, totalInsurance,
                in.principal().add(totalInterest).add(totalInsurance).setScale(2, RoundingMode.HALF_UP),
                ear.setScale(2, RoundingMode.HALF_UP), rows);
    }

    /** Nominal annual rate (%) to periodic monthly rate. */
    static BigDecimal monthlyRate(BigDecimal annualRatePct) {
        return annualRatePct.divide(BigDecimal.valueOf(1200), MC);
    }

    static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
