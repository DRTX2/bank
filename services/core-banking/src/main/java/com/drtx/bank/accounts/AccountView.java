package com.drtx.bank.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.drtx.bank.shared.web.Amounts;

public record AccountView(UUID id, String number, UUID ownerId, String ownerName, Account.Type type, String currency,
                          BigDecimal balance, Account.Status status, BigDecimal dailyLimit, String alias,
                          Instant createdAt) {

    public AccountView(Account a, String ownerName) {
        this(a.getId(), a.getNumber(), a.getOwnerId(), ownerName, a.getType(), a.getCurrency(),
                Amounts.fromMinor(a.getBalanceMinor()), a.getStatus(), Amounts.fromMinor(a.getDailyLimitMinor()),
                a.getAlias(), a.getCreatedAt());
    }
}
