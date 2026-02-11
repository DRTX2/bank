package com.drtx.bank.ledger.domain.models;

import java.math.BigDecimal;
import java.util.Objects;

public enum EntryType {

    DEBIT {
        @Override
        public BigDecimal apply(BigDecimal amount) {
            validate(amount);
            return amount.negate();
        }
    },

    CREDIT {
        @Override
        public BigDecimal apply(BigDecimal amount) {
            validate(amount);
            return amount;
        }
    };

    public abstract BigDecimal apply(BigDecimal amount);

    protected void validate(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}