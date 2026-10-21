package com.drtx.bank.shared.security;

import static com.drtx.bank.shared.security.Permission.*;

import java.util.EnumSet;
import java.util.Set;

/** Built-in roles provisioned for every institution. Tenant admins can add custom roles on top. */
public enum SystemRole {
    CUSTOMER("Retail customer", EnumSet.of(ACCOUNTS_READ_OWN, TRANSFERS_CREATE_OWN, TRANSFERS_READ_OWN,
            STATEMENTS_READ_OWN, SIMULATIONS_USE)),
    CORPORATE_CUSTOMER("Business customer with bulk payments", EnumSet.of(ACCOUNTS_READ_OWN, TRANSFERS_CREATE_OWN,
            TRANSFERS_READ_OWN, STATEMENTS_READ_OWN, SIMULATIONS_USE, PAYMENTS_BATCH)),
    TELLER("Branch teller", EnumSet.of(ACCOUNTS_READ_ANY, ACCOUNTS_OPEN, CUSTOMERS_ONBOARD, CASH_DEPOSIT, CASH_WITHDRAW,
            TRANSFERS_CREATE_ANY, TRANSFERS_READ_ANY, STATEMENTS_READ_ANY, USERS_READ, SIMULATIONS_USE)),
    BRANCH_MANAGER("Branch manager: teller powers plus four-eyes approvals", EnumSet.of(ACCOUNTS_READ_ANY, ACCOUNTS_OPEN,
            ACCOUNTS_FREEZE, CUSTOMERS_ONBOARD, CASH_DEPOSIT, CASH_WITHDRAW, TRANSFERS_CREATE_ANY, TRANSFERS_READ_ANY,
            TRANSFERS_APPROVE, STATEMENTS_READ_ANY, USERS_READ, OPS_MONITOR, SIMULATIONS_USE)),
    COMPLIANCE_OFFICER("AML / fraud analyst", EnumSet.of(RISK_REVIEW, ACCOUNTS_READ_ANY, ACCOUNTS_FREEZE,
            TRANSFERS_READ_ANY, AUDIT_READ, OPS_MONITOR, USERS_READ)),
    AUDITOR("Internal auditor (read-only)", EnumSet.of(ACCOUNTS_READ_ANY, TRANSFERS_READ_ANY, STATEMENTS_READ_ANY,
            AUDIT_READ, USERS_READ)),
    TENANT_ADMIN("Institution administrator", EnumSet.of(USERS_READ, USERS_MANAGE, ROLES_MANAGE, AUDIT_READ,
            OPS_MONITOR, OPS_SIMULATE, ACCOUNTS_READ_ANY, TRANSFERS_READ_ANY)),
    PLATFORM_ADMIN("Platform operator (SaaS owner)", EnumSet.of(TENANTS_MANAGE, USERS_READ, USERS_MANAGE,
            ROLES_MANAGE, AUDIT_READ, OPS_MONITOR));

    private final String description;
    private final Set<Permission> permissions;

    SystemRole(String description, Set<Permission> permissions) {
        this.description = description;
        this.permissions = permissions;
    }

    public String description() {
        return description;
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
