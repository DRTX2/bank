package com.drtx.bank.ledger.models;

import java.math.BigDecimal;

public enum EntryType {
    DEBIT{
        @Override
        public BigDecimal apply(BigDecimal amount) {
            return amount.negate();
        }
    },
    CREDIT{
        @Override
        public BigDecimal apply(BigDecimal amount) {
            return amount;
        }
    }

    public abstract BigDecimal apply(BigDecimal amount);
}
