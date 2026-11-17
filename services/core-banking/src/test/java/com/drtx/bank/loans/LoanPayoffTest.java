package com.drtx.bank.loans;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LoanPayoffTest {

    private static LoanInstallment due(int number, String date, long interestMinor) {
        return new LoanInstallment(UUID.randomUUID(), UUID.randomUUID(), number, LocalDate.parse(date), 10_000,
                interestMinor);
    }

    @Test
    void theRunningPeriodAccruesProRataByDays() {
        // November has 30 days: 15 elapsed of the 12.00 scheduled for the period
        assertThat(LoanService.accruedInterest(List.of(due(1, "2026-12-01", 1_200)), LocalDate.parse("2026-11-16")))
                .isEqualTo(600);
        assertThat(LoanService.accruedInterest(List.of(due(1, "2026-12-01", 1_200)), LocalDate.parse("2026-11-01")))
                .isZero();
    }

    @Test
    void periodsAlreadyDueAreOwedInFull() {
        var open = List.of(due(1, "2026-11-01", 1_200), due(2, "2026-12-01", 1_100), due(3, "2027-01-01", 1_000));
        assertThat(LoanService.accruedInterest(open, LocalDate.parse("2026-11-16"))).isEqualTo(1_200 + 550);
    }
}
