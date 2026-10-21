package com.drtx.bank.accounts;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.drtx.bank.identity.User;
import com.drtx.bank.identity.UserService;
import com.drtx.bank.ledger.JournalEntry;
import com.drtx.bank.ledger.LedgerService;
import com.drtx.bank.platform.Tenant;
import com.drtx.bank.platform.TenantRepository;
import com.drtx.bank.shared.audit.AuditService;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.Permission;
import com.drtx.bank.shared.security.SystemRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    static final long DEFAULT_DAILY_LIMIT_MINOR = 10_000_00;

    private final AccountRepository accounts;
    private final TenantRepository tenants;
    private final UserService users;
    private final LedgerService ledger;
    private final AuditService audit;

    AccountService(AccountRepository accounts, TenantRepository tenants, UserService users, LedgerService ledger,
                   AuditService audit) {
        this.accounts = accounts;
        this.tenants = tenants;
        this.users = users;
        this.ledger = ledger;
        this.audit = audit;
    }

    /** Settlement account that mirrors cash held by the institution (counterpart of deposits/withdrawals). */
    @Transactional
    public Account openVault(Tenant tenant) {
        return accounts.save(new Account(tenant.getId(), AccountNumbers.generate(tenant.getCountry(), tenant.getCode()),
                null, Account.Type.INTERNAL, tenant.getCurrency(), Long.MAX_VALUE, "Cash vault"));
    }

    @Transactional
    public Account open(CurrentUser actor, UUID ownerId, Account.Type type, String alias) {
        if (type == Account.Type.INTERNAL) {
            throw new DomainException(ErrorCode.VALIDATION, "Internal accounts are system-managed");
        }
        User owner = users.get(ownerId);
        Tenant tenant = tenants.findById(owner.getTenantId()).orElseThrow();
        Account account = accounts.save(new Account(tenant.getId(),
                AccountNumbers.generate(tenant.getCountry(), tenant.getCode()), owner.getId(), type,
                tenant.getCurrency(), DEFAULT_DAILY_LIMIT_MINOR, alias));
        audit.record(tenant.getId(), actor, "ACCOUNT_OPENED", "account", account.getId(), account.getNumber());
        return account;
    }

    /** KYC onboarding: customer user and first savings account in one transaction. */
    @Transactional
    public Account onboardCustomer(CurrentUser actor, String email, String fullName, String nationalId,
                                   String password, boolean corporate) {
        String role = corporate ? SystemRole.CORPORATE_CUSTOMER.name() : SystemRole.CUSTOMER.name();
        User customer = users.create(actor, email, fullName, nationalId, password, List.of(role));
        return open(actor, customer.getId(), Account.Type.SAVINGS, "Ahorros");
    }

    @Transactional(readOnly = true)
    public List<AccountView> visibleTo(CurrentUser user) {
        if (!user.has(Permission.ACCOUNTS_READ_ANY)) {
            return accounts.findByOwnerIdOrderByCreatedAt(user.id()).stream()
                    .map(a -> new AccountView(a, user.name())).toList();
        }
        Map<UUID, String> names = users.list().stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return accounts.findByTypeNotOrderByCreatedAtDesc(Account.Type.INTERNAL).stream()
                .map(a -> new AccountView(a, names.get(a.getOwnerId()))).toList();
    }

    /** Ownership check. Unauthorized reads look exactly like missing accounts (no enumeration). */
    @Transactional(readOnly = true)
    public Account readable(CurrentUser user, UUID id) {
        Account account = accounts.findById(id).orElseThrow(() -> DomainException.notFound("Account"));
        boolean allowed = user.has(Permission.ACCOUNTS_READ_ANY)
                || (user.has(Permission.ACCOUNTS_READ_OWN) && account.isOwnedBy(user.id()));
        if (!allowed || account.getType() == Account.Type.INTERNAL) {
            throw DomainException.notFound("Account");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public AccountView view(CurrentUser user, UUID id) {
        Account a = readable(user, id);
        return new AccountView(a, ownerName(a));
    }

    /** Destination lookup for transfers: only the masked holder name is revealed. */
    @Transactional(readOnly = true)
    public Lookup lookup(String number) {
        if (!AccountNumbers.isValid(number)) {
            throw new DomainException(ErrorCode.INVALID_ACCOUNT_NUMBER, "Invalid account number (check digits)");
        }
        Account a = accounts.findByNumber(AccountNumbers.normalize(number))
                .filter(x -> x.getType() != Account.Type.INTERNAL)
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_ACCOUNT_NUMBER, "Account not found"));
        return new Lookup(a.getNumber(), mask(ownerName(a)), a.getCurrency(), a.getStatus());
    }

    public record Lookup(String number, String holder, String currency, Account.Status status) {
    }

    @Transactional
    public Account changeStatus(CurrentUser actor, UUID id, Account.Status status) {
        Account account = readable(actor, id);
        account.changeStatus(status);
        audit.record(actor.tenantId(), actor, "ACCOUNT_" + status, "account", id, account.getNumber());
        return account;
    }

    @Transactional
    public JournalEntry cash(CurrentUser actor, UUID accountId, long amountMinor, boolean deposit, String reference) {
        Account account = readable(actor, accountId);
        Account vault = accounts.findFirstByType(Account.Type.INTERNAL).orElseThrow();
        long signed = deposit ? amountMinor : -amountMinor;
        JournalEntry journal = ledger.post(actor.tenantId(),
                deposit ? JournalEntry.Type.CASH_DEPOSIT : JournalEntry.Type.CASH_WITHDRAWAL,
                reference == null ? "TELLER" : reference, deposit ? "Depósito en ventanilla" : "Retiro en ventanilla",
                account.getCurrency(), List.of(new LedgerService.Leg(account.getId(), signed),
                        new LedgerService.Leg(vault.getId(), -signed)));
        audit.record(actor.tenantId(), actor, deposit ? "CASH_DEPOSIT" : "CASH_WITHDRAWAL", "account", accountId,
                String.valueOf(amountMinor));
        return journal;
    }

    private String ownerName(Account a) {
        return a.getOwnerId() == null ? null : users.get(a.getOwnerId()).getFullName();
    }

    static String mask(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String part : name.split(" ")) {
            if (!part.isEmpty()) {
                out.append(part.charAt(0)).append("*".repeat(Math.max(part.length() - 1, 2))).append(' ');
            }
        }
        return out.toString().trim();
    }
}
