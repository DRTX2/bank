package com.drtx.bank.shared.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Single source of truth for authorization. Codes follow {@code resource:action[:scope]}; "own" vs "any"
 * expresses data ownership (customers see their own accounts, staff see the whole tenant).
 * The catalogue is synced to the {@code permissions} table at startup and guarded by an architecture test
 * that checks every {@code @PreAuthorize} references an existing code. Descriptions are product copy (Spanish, like the UI).
 */
public enum Permission {
    ACCOUNTS_READ_OWN("accounts:read:own", "Ver sus cuentas y movimientos"),
    ACCOUNTS_READ_ANY("accounts:read:any", "Ver cualquier cuenta de la institución"),
    ACCOUNTS_OPEN("accounts:open", "Abrir cuentas a clientes"),
    ACCOUNTS_FREEZE("accounts:freeze", "Congelar y descongelar cuentas"),
    CUSTOMERS_ONBOARD("customers:onboard", "Registrar clientes nuevos (KYC)"),
    CASH_DEPOSIT("cash:deposit", "Registrar depósitos en ventanilla"),
    CASH_WITHDRAW("cash:withdraw", "Registrar retiros en ventanilla"),
    TRANSFERS_CREATE_OWN("transfers:create:own", "Transferir desde sus cuentas"),
    TRANSFERS_CREATE_ANY("transfers:create:any", "Transferir a nombre de clientes"),
    TRANSFERS_READ_OWN("transfers:read:own", "Ver sus transferencias"),
    TRANSFERS_READ_ANY("transfers:read:any", "Ver cualquier transferencia"),
    TRANSFERS_APPROVE("transfers:approve", "Aprobar transferencias grandes (cuatro ojos)"),
    PAYMENTS_BATCH("payments:batch", "Cargar pagos masivos ISO 20022 pain.001"),
    STATEMENTS_READ_OWN("statements:read:own", "Descargar sus estados de cuenta ISO 20022"),
    STATEMENTS_READ_ANY("statements:read:any", "Descargar cualquier estado de cuenta ISO 20022"),
    RISK_REVIEW("risk:review", "Liberar o bloquear transferencias retenidas por riesgo"),
    USERS_READ("users:read", "Ver usuarios del personal y clientes"),
    USERS_MANAGE("users:manage", "Crear usuarios, asignar roles, bloquear accesos"),
    ROLES_MANAGE("roles:manage", "Crear y editar roles propios"),
    AUDIT_READ("audit:read", "Leer y verificar la bitácora de auditoría"),
    OPS_MONITOR("ops:monitor", "Ver el tablero de operaciones en vivo"),
    OPS_SIMULATE("ops:simulate", "Ejecutar el simulador de tráfico"),
    DAY_CLOSE("ops:close-day", "Ejecutar el cierre de día y abonar intereses"),
    LOANS_ORIGINATE("loans:originate", "Otorgar créditos y desembolsarlos"),
    LOANS_READ_OWN("loans:read:own", "Ver sus créditos y su tabla de pagos"),
    LOANS_READ_ANY("loans:read:any", "Ver los créditos de la institución"),
    SIMULATIONS_USE("simulations:use", "Usar los simuladores financieros"),
    TENANTS_MANAGE("tenants:manage", "Dar de alta y suspender instituciones", Scope.PLATFORM);

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
