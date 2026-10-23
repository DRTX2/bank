package com.drtx.bank.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DayCloseServiceTest {

    @Test
    void oneDayOfInterestIsActual365RoundedDownToTheCent() {
        // 10,000.00 at 3.00 %: 0.8219 a day
        assertThat(DayCloseService.dailyInterest(1_000_000, 300)).isEqualTo(82);
        // 121.66 earns 0.0099 a day: below one cent, nothing is credited
        assertThat(DayCloseService.dailyInterest(12_166, 300)).isZero();
        assertThat(DayCloseService.dailyInterest(0, 300)).isZero();
    }

    @Test
    void refusesToOverflowInsteadOfCreditingGarbage() {
        assertThatThrownBy(() -> DayCloseService.dailyInterest(Long.MAX_VALUE / 100, 300))
                .isInstanceOf(ArithmeticException.class);
    }
}
