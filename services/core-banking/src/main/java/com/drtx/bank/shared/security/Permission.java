package com.drtx.bank.shared.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Single source of truth for authorization. Codes follow {@code resource:action[:scope]}; "own" vs "any"
 * expresses data ownership (customers see their own accounts, staff see the whole tenant).
 * The catalogue is synced to the {@code permissions} table at startup and guarded by an architecture test
 * that checks every {@code @PreAuthorize} references an existing code.
 */
public enum Permission {
    ACCOUNTS_READ_OWN("accounts:read:own", "View own accounts and movements"),
    ACCOUNTS_READ_ANY("accounts:read:any", "View any account in the institution"),
    ACCOUNTS_OPEN("accounts:open", "Open accounts for customers"),
    ACCOUNTS_FREEZE("accounts:freeze", "Freeze and unfreeze accounts"),
    CUSTOMERS_ONBOARD("customers:onboard", "Register new customers (KYC onboarding)"),
    CASH_DEPOSIT("cash:deposit", "Register cash deposits at the teller"),
    CASH_WITHDRAW("cash:withdraw", "Register cash withdrawals at the teller"),
    TRANSFERS_CREATE_OWN("transfers:create:own", "Send transfers from own accounts"),
    TRANSFERS_CREATE_ANY("transfers:create:any", "Send transfers on behalf of customers"),
    TRANSFERS_READ_OWN("transfers:read:own", "View own transfers"),
    TRANSFERS_READ_ANY("transfers:read:any", "View any transfer"),
    TRANSFERS_APPROVE("transfers:approve", "Approve high-value transfers (four-eyes)"),
    PAYMENTS_BATCH("payments:batch", "Upload ISO 20022 pain.001 bulk payments"),
    STATEMENTS_READ_OWN("statements:read:own", "Download own ISO 20022 statements"),
    STATEMENTS_READ_ANY("statements:read:any", "Download any ISO 20022 statement"),
    RISK_REVIEW("risk:review", "Release or block transfers held by the risk engine"),
    USERS_READ("users:read", "View staff and customer users"),
    USERS_MANAGE("users:manage", "Create users, assign roles, lock accounts"),
    ROLES_MANAGE("roles:manage", "Create and edit custom roles"),
    AUDIT_READ("audit:read", "Read and verify the tamper-evident audit trail"),
    OPS_MONITOR("ops:monitor", "Live operations dashboard"),
    OPS_SIMULATE("ops:simulate", "Run the synthetic traffic simulator"),
    SIMULATIONS_USE("simulations:use", "Use financial simulators"),
    TENANTS_MANAGE("tenants:manage", "Provision and suspend institutions", Scope.PLATFORM);

    public enum Scope { TENANT, PLATFORM }

    private final String code;
    private final String description;
    private final Scope scope;

    Permission(String code, String description) {
        this(code, description, Scope.TENANT);
    }

    Permission(String code, String description, Scope scope) {
        this.code = code;
        this.description = description;
        this.scope = scope;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public Scope scope() {
        return scope;
    }

    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }
}
