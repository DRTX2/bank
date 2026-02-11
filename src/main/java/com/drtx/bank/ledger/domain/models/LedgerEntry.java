package com.drtx.bank.ledger.domain.models;

import java.math.BigDecimal;

public class LedgerEntry {
    private Long id;
    private Long accountId;
    private BigDecimal amount;
    private EntryType type;
}
