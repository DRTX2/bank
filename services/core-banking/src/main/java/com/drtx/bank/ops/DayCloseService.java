package com.drtx.bank.ops;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.drtx.bank.accounts.Account;
import com.drtx.bank.accounts.AccountRepository;
import com.drtx.bank.accounts.AccountService;
import com.drtx.bank.ledger.JournalEntry;
import com.drtx.bank.ledger.LedgerService;
import com.drtx.bank.platform.Tenant;
import com.drtx.bank.platform.TenantRepository;
import com.drtx.bank.shared.audit.AuditService;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.tenancy.TenantContext;
import com.drtx.bank.shared.tenancy.TenantScope;
import com.drtx.bank.shared.web.Amounts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-of-day close: credits one day of interest to every savings account in a single balanced journal entry
 * (interest expense against the customers), once per institution and business date.
 */
@Service
public class DayCloseService {

    private static final Logger log = LoggerFactory.getLogger(DayCloseService.class);

    private final DayCloseRepository closes;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final TenantRepository tenants;
    private final LedgerService ledger;
    private final AuditService audit;
    private final TenantScope scope;
    private final ZoneId zone;

    DayCloseService(DayCloseRepository closes, AccountRepository accounts, AccountService accountService,
                    TenantRepository tenants, LedgerService ledger, AuditService audit, TenantScope scope,
                    @Value("${bank.business-zone}") String zone) {
        this.closes = closes;
        this.accounts = accounts;
        this.accountService = accountService;
        this.tenants = tenants;
        this.ledger = ledger;
        this.audit = audit;
        this.scope = scope;
        this.zone = ZoneId.of(zone);
    }

    public record DayCloseView(LocalDate businessDate, int accountsCredited, BigDecimal interest, BigDecimal annualRate,
                               String closedBy, Instant closedAt) {
        static DayCloseView of(DayClose c) {
            return new DayCloseView(c.getBusinessDate(), c.getAccountsCredited(), Amounts.fromMinor(c.getInterestMinor()),
                    BigDecimal.valueOf(c.getRateBps(), 2), c.getClosedByEmail(), c.getClosedAt());
        }
    }

    public record Overview(LocalDate today, BigDecimal annualRate, boolean todayClosed, List<DayCloseView> closes) {
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        Tenant tenant = tenants.findById(TenantContext.require()).orElseThrow();
        LocalDate today = LocalDate.now(zone);
        return new Overview(today, BigDecimal.valueOf(tenant.getSavingsRateBps(), 2), closes.existsByBusinessDate(today),
                closes.findTop30ByOrderByBusinessDateDesc().stream().map(DayCloseView::of).toList());
    }

    @Transactional
    public DayCloseView closeToday(CurrentUser actor) {
        return DayCloseView.of(close(actor.tenantId(), actor, LocalDate.now(zone)));
    }

    /** Safety net: institutions nobody closed by hand are closed late in the business day. */
    @Scheduled(cron = "${bank.day-close.cron}", zone = "${bank.business-zone}")
    void closeOpenInstitutions() {
        LocalDate today = LocalDate.now(zone);
        for (Tenant tenant : scope.asSystem(() -> tenants.findAll().stream().filter(Tenant::isActive).toList())) {
            try {
                scope.inTenant(tenant.getId(),
                        () -> closes.existsByBusinessDate(today) ? null : close(tenant.getId(), null, today));
            } catch (RuntimeException e) {
                log.error("Day close failed for {}", tenant.getCode(), e);
            }
        }
    }

    private DayClose close(UUID tenantId, CurrentUser actor, LocalDate date) {
        if (closes.existsByBusinessDate(date)) {
            throw new DomainException(ErrorCode.DAY_ALREADY_CLOSED, "Business day " + date + " is already closed");
        }
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        List<LedgerService.Leg> legs = new ArrayList<>();
        long total = 0;
        for (Account account : accounts.interestBearing()) {
            long interest = dailyInterest(account.getBalanceMinor(), tenant.getSavingsRateBps());
            if (interest > 0) {
                legs.add(new LedgerService.Leg(account.getId(), interest));
                total += interest;
            }
        }
        int credited = legs.size();
        UUID journalId = null;
        if (total > 0) {
            Account expense = accountService.internal(tenant, Account.INTEREST_EXPENSE);
            legs.add(new LedgerService.Leg(expense.getId(), -total));
            journalId = ledger.post(tenantId, JournalEntry.Type.INTEREST, "EOD-" + date, "Intereses de ahorro",
                    tenant.getCurrency(), legs).getId();
        }
        DayClose close = closes.saveAndFlush(new DayClose(tenantId, date, actor == null ? null : actor.email(),
                credited, total, tenant.getSavingsRateBps(), journalId));
        audit.record(tenantId, actor, "DAY_CLOSED", "day", date,
                "accounts=" + credited + " interest=" + total + " rateBps=" + tenant.getSavingsRateBps());
        return close;
    }

    /** One day of interest, actual/365, rounded down to the cent. */
    // shortcut: the sub-cent remainder is dropped every day; carry it per account when monthly interest must be exact.
    static long dailyInterest(long balanceMinor, int rateBps) {
        return Math.multiplyExact(balanceMinor, rateBps) / (10_000L * 365);
    }
}
