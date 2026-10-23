package com.drtx.bank.demo;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.drtx.bank.accounts.Account;
import com.drtx.bank.accounts.AccountService;
import com.drtx.bank.identity.RoleService;
import com.drtx.bank.identity.UserService;
import com.drtx.bank.loans.LoanService;
import com.drtx.bank.platform.Tenant;
import com.drtx.bank.platform.TenantRepository;
import com.drtx.bank.platform.TenantService;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.Permission;
import com.drtx.bank.shared.security.SystemRole;
import com.drtx.bank.shared.tenancy.TenantScope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Demo institutions and users (all with password {@value #PASSWORD}). Enabled only with {@code DEMO_SEED=true};
 * never in production. Idempotent: skips if the platform tenant already exists.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "bank.demo.seed", havingValue = "true")
class DemoDataSeeder implements ApplicationRunner {

    static final String PASSWORD = "Kuntur2026!";
    /** Published on purpose, like the password: lets reviewers try MFA and step-up approvals without a phone. */
    static final String DEMO_MFA_SECRET = "KUNTURDEMOGERENTEMFAANDINAKUNTUR";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final TenantRepository tenants;
    private final TenantService tenantService;
    private final RoleService roles;
    private final UserService users;
    private final AccountService accounts;
    private final LoanService loans;
    private final TenantScope scope;

    DemoDataSeeder(TenantRepository tenants, TenantService tenantService, RoleService roles, UserService users,
                   AccountService accounts, LoanService loans, TenantScope scope) {
        this.tenants = tenants;
        this.tenantService = tenantService;
        this.roles = roles;
        this.users = users;
        this.accounts = accounts;
        this.loans = loans;
        this.scope = scope;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenants.findByCode("KNTR").isPresent()) {
            return;
        }
        log.info("Seeding demo data");
        Tenant platform = new Tenant("KNTR", "Kuntur Plataforma", "EC", "USD", "KNTRECE1XXX", 1_000_000_00);
        scope.inTenant(platform.getId(), () -> {
            tenants.save(platform);
            roles.provisionPlatformRoles(platform.getId());
            return users.createUser(platform.getId(), "root@kuntur.dev", "Operador de plataforma", null, PASSWORD,
                    List.of(SystemRole.PLATFORM_ADMIN.name()));
        });
        CurrentUser operator = CurrentUser.system(platform.getId(), EnumSet.allOf(Permission.class));

        Tenant andina = tenantService.provision(operator, new TenantService.ProvisionCommand("ANDN",
                "Cooperativa Andina", "EC", "USD", "ANDNECE1XXX", 5_000_00, "admin@andina.ec", "Valeria Paredes", PASSWORD));
        seedInstitution(andina, "andina.ec", List.of(
                new Staff("cajero", "Diego Quishpe", SystemRole.TELLER),
                new Staff("gerente", "Martina Salazar", SystemRole.BRANCH_MANAGER, DEMO_MFA_SECRET),
                new Staff("cumplimiento", "Andrés Villacís", SystemRole.COMPLIANCE_OFFICER),
                new Staff("auditor", "Lucía Cárdenas", SystemRole.AUDITOR)), List.of(
                new Customer("ana", "Ana Morales", "1712345678", true, 25_000_00, 4_500_00),
                new Customer("luis", "Luis Guamán", "1723456789", false, 3_500_00),
                new Customer("sofia", "Sofía Andrade", "1734567890", false, 8_200_00),
                new Customer("mateo", "Mateo Chicaiza", "1745678901", false, 1_250_00),
                new Customer("camila", "Camila Ortiz", "1756789012", false, 15_600_00, 12_000_00),
                new Customer("jorge", "Jorge Pilataxi", "1767890123", false, 640_00),
                new Customer("elena", "Elena Rivadeneira", "1778901234", false, 4_100_00)));

        Tenant pacifico = tenantService.provision(operator, new TenantService.ProvisionCommand("PACF",
                "Pacífico Fintech", "EC", "USD", "PACFECG1XXX", 2_000_00, "admin@pacifico.ec", "Rafael Mendoza", PASSWORD));
        seedInstitution(pacifico, "pacifico.ec", List.of(
                new Staff("cajero", "Paúl Intriago", SystemRole.TELLER),
                new Staff("cumplimiento", "Gabriela Torres", SystemRole.COMPLIANCE_OFFICER)), List.of(
                new Customer("carlos", "Carlos Vera", "0912345678", false, 2_000_00),
                new Customer("maria", "María Zambrano", "0923456789", true, 12_000_00),
                new Customer("pedro", "Pedro Alvarado", "0934567890", false, 900_00)));
        log.info("Demo data ready. Password for every demo user: {}", PASSWORD);
    }

    private record Staff(String user, String name, SystemRole role, String mfaSecret) {
        Staff(String user, String name, SystemRole role) {
            this(user, name, role, null);
        }
    }

    /**
     * {@code loanMinor} > 0 grants a demo loan (24 months at 14.50 %) into the savings account; above the
     * institution's four-eyes threshold it waits for the branch manager's approval.
     */
    private record Customer(String user, String name, String nationalId, boolean corporate, long openingMinor,
                            long loanMinor) {
        Customer(String user, String name, String nationalId, boolean corporate, long openingMinor) {
            this(user, name, nationalId, corporate, openingMinor, 0);
        }
    }

    private void seedInstitution(Tenant tenant, String domain, List<Staff> staff, List<Customer> customers) {
        CurrentUser seeder = CurrentUser.system(tenant.getId(), EnumSet.allOf(Permission.class));
        scope.inTenant(tenant.getId(), () -> {
            for (Staff s : staff) {
                var user = users.createUser(tenant.getId(), s.user() + "@" + domain, s.name(), null, PASSWORD,
                        List.of(s.role().name()));
                if (s.mfaSecret() != null) {
                    users.enableDemoMfa(user, s.mfaSecret());
                }
            }
            for (Customer c : customers) {
                Account savings = accounts.onboardCustomer(seeder, c.user() + "@" + domain, c.name(), c.nationalId(),
                        PASSWORD, c.corporate());
                accounts.cash(seeder, savings.getId(), c.openingMinor(), true, "APERTURA");
                if (c.loanMinor() > 0) {
                    loans.originate(seeder, savings.getId(), c.loanMinor(), 1450, 24);
                }
                if (c.corporate()) {
                    UUID ownerId = savings.getOwnerId();
                    Account checking = accounts.open(seeder, ownerId, Account.Type.CHECKING, "Corriente empresarial");
                    accounts.cash(seeder, checking.getId(), c.openingMinor() / 2, true, "APERTURA");
                }
            }
            return null;
        });
    }
}
