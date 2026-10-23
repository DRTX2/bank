package com.drtx.bank.loans;

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
import com.drtx.bank.identity.UserService;
import com.drtx.bank.ledger.JournalEntry;
import com.drtx.bank.ledger.LedgerService;
import com.drtx.bank.ledger.LedgerService.Leg;
import com.drtx.bank.platform.Tenant;
import com.drtx.bank.platform.TenantRepository;
import com.drtx.bank.realtime.CustomerNotices;
import com.drtx.bank.shared.audit.AuditService;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.Permission;
import com.drtx.bank.shared.tenancy.TenantScope;
import com.drtx.bank.shared.web.Amounts;
import com.drtx.bank.simulations.LoanSimulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loans with a French amortization schedule. Disbursement credits the customer against the loan portfolio; each
 * installment debits the customer at the day close (principal back to the portfolio, interest to interest income).
 */
@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final LoanRepository loans;
    private final LoanInstallmentRepository installments;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final UserService users;
    private final TenantRepository tenants;
    private final LedgerService ledger;
    private final AuditService audit;
    private final CustomerNotices notices;
    private final TenantScope scope;
    private final ZoneId zone;

    LoanService(LoanRepository loans, LoanInstallmentRepository installments, AccountRepository accounts,
                AccountService accountService, UserService users, TenantRepository tenants, LedgerService ledger,
                AuditService audit, CustomerNotices notices, TenantScope scope,
                @Value("${bank.business-zone}") String zone) {
        this.loans = loans;
        this.installments = installments;
        this.accounts = accounts;
        this.accountService = accountService;
        this.users = users;
        this.tenants = tenants;
        this.ledger = ledger;
        this.audit = audit;
        this.notices = notices;
        this.scope = scope;
        this.zone = ZoneId.of(zone);
    }

    public record InstallmentView(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest,
                                  BigDecimal amount, String status) {
    }

    public record LoanView(UUID id, String customerName, String accountNumber, BigDecimal principal,
                           BigDecimal outstanding, BigDecimal annualRate, int termMonths, Loan.Status status,
                           Instant createdAt, InstallmentView next) {
    }

    public record LoanDetail(LoanView loan, List<InstallmentView> schedule) {
    }

    public record Collection(int collected, int missed) {
    }

    @Transactional
    public LoanView originate(CurrentUser actor, UUID accountId, long principalMinor, int annualRateBps, int months) {
        Account account = accounts.findById(accountId)
                .filter(a -> a.getType() != Account.Type.INTERNAL && a.getOwnerId() != null)
                .orElseThrow(() -> DomainException.notFound("Account"));
        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new DomainException(ErrorCode.ACCOUNT_BLOCKED, "Account is " + account.getStatus());
        }
        Tenant tenant = tenants.findById(actor.tenantId()).orElseThrow();
        Loan loan = loans.save(new Loan(actor.tenantId(), account.getOwnerId(), accountId, principalMinor,
                annualRateBps, months, actor.email()));
        LocalDate today = LocalDate.now(zone);
        LoanSimulator.Result plan = LoanSimulator.simulate(new LoanSimulator.Input(Amounts.fromMinor(principalMinor),
                BigDecimal.valueOf(annualRateBps, 2), months, LoanSimulator.Method.FRENCH, null));
        for (LoanSimulator.Row row : plan.schedule()) {
            installments.save(new LoanInstallment(actor.tenantId(), loan.getId(), row.period(),
                    today.plusMonths(row.period()), cents(row.principal()), cents(row.interest())));
        }
        Account portfolio = accountService.internal(tenant, Account.LOAN_PORTFOLIO);
        ledger.post(actor.tenantId(), JournalEntry.Type.LOAN_DISBURSEMENT, "LOAN-" + loan.getId(),
                "Desembolso de crédito", account.getCurrency(),
                List.of(new Leg(accountId, principalMinor), new Leg(portfolio.getId(), -principalMinor)));
        notices.send(actor.tenantId(), account.getOwnerId(), CustomerNotices.Type.LoanDisbursed, principalMinor,
                account.getCurrency(), null, months);
        audit.record(actor.tenantId(), actor, "LOAN_ORIGINATED", "loan", loan.getId(),
                "amount=" + principalMinor + " rateBps=" + annualRateBps + " months=" + months);
        return view(loan, today);
    }

    @Transactional(readOnly = true)
    public List<LoanView> list(CurrentUser actor) {
        LocalDate today = LocalDate.now(zone);
        List<Loan> visible = actor.has(Permission.LOANS_READ_ANY) ? loans.findAllByOrderByCreatedAtDesc()
                : loans.findByCustomerIdOrderByCreatedAtDesc(actor.id());
        return visible.stream().map(l -> view(l, today)).toList();
    }

    @Transactional(readOnly = true)
    public LoanDetail detail(CurrentUser actor, UUID id) {
        Loan loan = loans.findById(id)
                .filter(l -> actor.has(Permission.LOANS_READ_ANY) || l.getCustomerId().equals(actor.id()))
                .orElseThrow(() -> DomainException.notFound("Loan"));
        LocalDate today = LocalDate.now(zone);
        return new LoanDetail(view(loan, today), installments.findByLoanIdOrderByNumber(id).stream()
                .map(i -> view(i, today)).toList());
    }

    /**
     * Day close: debits every installment due by {@code date}, each in its own transaction so that one short or
     * frozen account does not block the rest. Missed installments stay overdue and are retried at the next close.
     */
    public Collection collectDue(UUID tenantId, LocalDate date) {
        int collected = 0;
        int missed = 0;
        for (UUID id : scope.inTenant(tenantId, () -> installments.dueBy(date))) {
            try {
                scope.inTenant(tenantId, () -> collect(id));
                collected++;
            } catch (DomainException e) {
                missed++;
                notifyMissed(tenantId, id);
            } catch (RuntimeException e) {
                missed++;
                log.warn("Installment {} not collected", id, e);
            }
        }
        return new Collection(collected, missed);
    }

    private Void collect(UUID installmentId) {
        LoanInstallment due = installments.findById(installmentId).orElseThrow();
        if (due.getPaidAt() != null) {
            return null;
        }
        Loan loan = loans.findById(due.getLoanId()).orElseThrow();
        Tenant tenant = tenants.findById(loan.getTenantId()).orElseThrow();
        List<Leg> legs = new ArrayList<>(List.of(new Leg(loan.getAccountId(), -due.amountMinor())));
        if (due.getPrincipalMinor() > 0) {
            legs.add(new Leg(accountService.internal(tenant, Account.LOAN_PORTFOLIO).getId(), due.getPrincipalMinor()));
        }
        if (due.getInterestMinor() > 0) {
            legs.add(new Leg(accountService.internal(tenant, Account.INTEREST_INCOME).getId(), due.getInterestMinor()));
        }
        JournalEntry journal = ledger.post(loan.getTenantId(), JournalEntry.Type.LOAN_REPAYMENT,
                "LOAN-" + loan.getId() + "-" + due.getNumber(),
                "Cuota " + due.getNumber() + "/" + loan.getTermMonths() + " de crédito", tenant.getCurrency(), legs);
        due.paid(journal.getId());
        loan.repay(due.getPrincipalMinor());
        notices.send(loan.getTenantId(), loan.getCustomerId(), CustomerNotices.Type.InstallmentCollected,
                due.amountMinor(), tenant.getCurrency(), due.getNumber(), loan.getTermMonths());
        audit.record(loan.getTenantId(), null, "LOAN_INSTALLMENT_COLLECTED", "loan", loan.getId(),
                "installment=" + due.getNumber() + " amount=" + due.amountMinor());
        return null;
    }

    /** Tells the customer, in a transaction of its own: the collection attempt itself was rolled back. */
    private void notifyMissed(UUID tenantId, UUID installmentId) {
        scope.inTenant(tenantId, () -> {
            LoanInstallment due = installments.findById(installmentId).orElseThrow();
            Loan loan = loans.findById(due.getLoanId()).orElseThrow();
            notices.send(tenantId, loan.getCustomerId(), CustomerNotices.Type.InstallmentMissed, due.amountMinor(),
                    tenants.findById(tenantId).orElseThrow().getCurrency(), due.getNumber(), loan.getTermMonths());
            return null;
        });
    }

    /** Simulator rows are already rounded to the cent; zero is valid here (interest at 0 %), unlike API input. */
    private static long cents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private LoanView view(Loan loan, LocalDate today) {
        InstallmentView next = installments.findFirstByLoanIdAndPaidAtIsNullOrderByNumber(loan.getId())
                .map(i -> view(i, today)).orElse(null);
        String number = accounts.findById(loan.getAccountId()).map(Account::getNumber).orElse(null);
        return new LoanView(loan.getId(), users.get(loan.getCustomerId()).getFullName(), number,
                Amounts.fromMinor(loan.getPrincipalMinor()), Amounts.fromMinor(loan.getOutstandingMinor()),
                BigDecimal.valueOf(loan.getAnnualRateBps(), 2), loan.getTermMonths(), loan.getStatus(),
                loan.getCreatedAt(), next);
    }

    private static InstallmentView view(LoanInstallment i, LocalDate today) {
        String status = i.getPaidAt() != null ? "PAID" : i.getDueDate().isBefore(today) ? "OVERDUE" : "PENDING";
        return new InstallmentView(i.getNumber(), i.getDueDate(), Amounts.fromMinor(i.getPrincipalMinor()),
                Amounts.fromMinor(i.getInterestMinor()), Amounts.fromMinor(i.amountMinor()), status);
    }
}
