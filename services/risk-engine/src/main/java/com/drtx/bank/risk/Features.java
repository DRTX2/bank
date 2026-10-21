package com.drtx.bank.risk;

import java.time.LocalTime;

/** Everything the rules need, computed from the feature store for one scoring request. */
public record Features(long amountMinor, int attemptsLast10Min, long averageAmountMinor, int historySize,
                       boolean knownBeneficiary, int distinctSendersToDestination30Min, LocalTime localTime) {
}
