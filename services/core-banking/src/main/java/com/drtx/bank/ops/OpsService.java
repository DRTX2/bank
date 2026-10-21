package com.drtx.bank.ops;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

import com.drtx.bank.accounts.AccountRepository;
import com.drtx.bank.payments.Transfer;
import com.drtx.bank.payments.TransferService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OpsService {

    private final TransferService transfers;
    private final AccountRepository accounts;

    OpsService(TransferService transfers, AccountRepository accounts) {
        this.transfers = transfers;
        this.accounts = accounts;
    }

    record StatusStat(long count, BigDecimal amount) {
    }

    record Dashboard(Instant since, Map<Transfer.Status, StatusStat> transfers, long customerAccounts,
                     BigDecimal customerDeposits) {
    }

    @Transactional(readOnly = true)
    Dashboard dashboard() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        Map<Transfer.Status, StatusStat> byStatus = new EnumMap<>(Transfer.Status.class);
        for (Transfer.Status s : Transfer.Status.values()) {
            byStatus.put(s, new StatusStat(0, BigDecimal.ZERO.setScale(2)));
        }
        for (Object[] row : transfers.statsSince(since)) {
            byStatus.put((Transfer.Status) row[0], new StatusStat((Long) row[1], BigDecimal.valueOf((Long) row[2], 2)));
        }
        var customer = accounts.customerTotals();
        return new Dashboard(since, byStatus, customer.count(), BigDecimal.valueOf(customer.sum(), 2));
    }
}
