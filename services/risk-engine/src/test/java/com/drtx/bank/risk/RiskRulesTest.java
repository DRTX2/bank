package com.drtx.bank.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class RiskRulesTest {

    private static final LocalTime NOON = LocalTime.NOON;

    @Test
    void ordinaryPaymentToKnownBeneficiaryScoresZero() {
        RiskRules.Score s = RiskRules.evaluate(new Features(45_00, 0, 50_00, 12, true, 1, NOON));
        assertThat(s.value()).isZero();
        assertThat(s.signals()).isEmpty();
    }

    @Test
    void amountSpikeOnNewBeneficiaryIsFlagged() {
        RiskRules.Score s = RiskRules.evaluate(new Features(3_250_00, 0, 40_00, 10, false, 1, NOON));
        assertThat(s.signals()).extracting(RiskRules.Signal::code)
                .containsExactlyInAnyOrder("ELEVATED_AMOUNT", "AMOUNT_SPIKE", "NEW_BENEFICIARY");
        assertThat(s.value()).isEqualTo(60);
    }

    @Test
    void burstsAndMuleFanInAddUp() {
        RiskRules.Score s = RiskRules.evaluate(new Features(100_00, 6, 100_00, 10, false, 5, LocalTime.of(3, 0)));
        assertThat(s.signals()).extracting(RiskRules.Signal::code)
                .contains("VELOCITY_10M", "MULE_FAN_IN", "ODD_HOURS", "NEW_BENEFICIARY");
        assertThat(s.value()).isEqualTo(75);
    }

    @Test
    void scoreIsCappedAt100() {
        RiskRules.Score s = RiskRules.evaluate(new Features(9_000_00, 9, 10_00, 10, false, 9, LocalTime.of(2, 0)));
        assertThat(s.value()).isEqualTo(100);
    }

    @Test
    void spikeNeedsEnoughHistory() {
        RiskRules.Score s = RiskRules.evaluate(new Features(900_00, 0, 10_00, 2, true, 1, NOON));
        assertThat(s.signals()).extracting(RiskRules.Signal::code).doesNotContain("AMOUNT_SPIKE");
    }
}
