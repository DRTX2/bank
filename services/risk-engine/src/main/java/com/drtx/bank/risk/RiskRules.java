package com.drtx.bank.risk;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Explainable rule-based scoring. Each rule contributes weight and a reason code, so an analyst sees exactly why a
 * transfer was held. Pure function: trivially unit-testable and replaceable by an ML model behind the same contract.
 */
public final class RiskRules {

    public record Signal(String code, int weight, String description) {
    }

    public record Score(int value, List<Signal> signals) {
    }

    private RiskRules() {
    }

    public static Score evaluate(Features f) {
        List<Signal> signals = new ArrayList<>();
        if (f.amountMinor() >= 5_000_00) {
            signals.add(new Signal("LARGE_AMOUNT", 35, "Amount at or above 5,000"));
        } else if (f.amountMinor() >= 2_000_00) {
            signals.add(new Signal("ELEVATED_AMOUNT", 20, "Amount at or above 2,000"));
        }
        if (f.historySize() >= 3 && f.amountMinor() > 5 * Math.max(f.averageAmountMinor(), 1)) {
            signals.add(new Signal("AMOUNT_SPIKE", 30, "More than 5x the customer's average transfer"));
        }
        if (f.attemptsLast10Min() >= 5) {
            signals.add(new Signal("VELOCITY_10M", 30, "5+ transfers from this account in 10 minutes"));
        } else if (f.attemptsLast10Min() >= 3) {
            signals.add(new Signal("VELOCITY_10M", 15, "3+ transfers from this account in 10 minutes"));
        }
        if (!f.knownBeneficiary()) {
            signals.add(new Signal("NEW_BENEFICIARY", 10, "First transfer to this beneficiary"));
        }
        if (f.distinctSendersToDestination30Min() >= 4) {
            signals.add(new Signal("MULE_FAN_IN", 25, "Destination received from 4+ different accounts in 30 minutes"));
        }
        LocalTime t = f.localTime();
        if (t.isBefore(LocalTime.of(5, 0))) {
            signals.add(new Signal("ODD_HOURS", 10, "Initiated between 00:00 and 05:00 local time"));
        }
        if (f.amountMinor() >= 1_000_00 && f.amountMinor() % 1_000_00 == 0) {
            signals.add(new Signal("ROUND_AMOUNT", 5, "Round amount (multiple of 1,000)"));
        }
        int score = Math.min(100, signals.stream().mapToInt(Signal::weight).sum());
        return new Score(score, signals);
    }
}
