package com.drtx.bank.shared.web;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;

/** Converts between API decimals ("125.50") and ledger minor units (12550). Two-decimal currencies only. */
public final class Amounts {

    private Amounts() {
    }

    public static long toMinor(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new DomainException(ErrorCode.VALIDATION, "Amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new DomainException(ErrorCode.VALIDATION, "Amount supports at most 2 decimals");
        }
        return amount.movePointRight(2).longValueExact();
    }

    public static BigDecimal fromMinor(long minor) {
        return BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
    }
}
