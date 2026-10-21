package com.drtx.bank.platform;

import java.util.List;
import java.util.UUID;

import com.drtx.bank.accounts.AccountService;
import com.drtx.bank.identity.RoleService;
import com.drtx.bank.identity.UserService;
import com.drtx.bank.shared.audit.AuditService;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.SystemRole;
import com.drtx.bank.shared.tenancy.TenantScope;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenants;
    private final RoleService roles;
    private final UserService users;
    private final AccountService accounts;
    private final AuditService audit;
    private final TenantScope scope;

    TenantService(TenantRepository tenants, RoleService roles, UserService users, AccountService accounts,
                  AuditService audit, TenantScope scope) {
        this.tenants = tenants;
        this.roles = roles;
        this.users = users;
        this.accounts = accounts;
        this.audit = audit;
        this.scope = scope;
    }

    public record ProvisionCommand(String code, String name, String country, String currency, String bic,
                                   long approvalThresholdMinor, String adminEmail, String adminName,
                                   String adminPassword) {
    }

    /**
     * Creates an institution with its system roles, settlement (vault) account and first administrator,
     * atomically, in a transaction bound to the new tenant.
     */
    public Tenant provision(CurrentUser operator, ProvisionCommand cmd) {
        if (tenants.findByCode(cmd.code()).isPresent()) {
            throw new DomainException(ErrorCode.CONFLICT, "Tenant code already in use");
        }
        Tenant tenant = new Tenant(cmd.code(), cmd.name(), cmd.country(), cmd.currency(), cmd.bic(),
                cmd.approvalThresholdMinor());
        return scope.inTenant(tenant.getId(), () -> {
            tenants.save(tenant);
            roles.provisionSystemRoles(tenant.getId());
            accounts.openVault(tenant);
            users.createUser(tenant.getId(), cmd.adminEmail(), cmd.adminName(), null, cmd.adminPassword(),
                    List.of(SystemRole.TENANT_ADMIN.name()));
            audit.record(tenant.getId(), operator, "TENANT_PROVISIONED", "tenant", tenant.getId(), cmd.name());
            return tenant;
        });
    }

    @Transactional(readOnly = true)
    public List<Tenant> all() {
        return tenants.findAll();
    }

    @Transactional
    public Tenant changeStatus(CurrentUser operator, UUID id, Tenant.Status status) {
        Tenant tenant = tenants.findById(id).orElseThrow(() -> DomainException.notFound("Tenant"));
        if (tenant.getId().equals(operator.tenantId())) {
            throw new DomainException(ErrorCode.INVALID_STATE, "The platform tenant cannot suspend itself");
        }
        tenant.changeStatus(status);
        audit.record(operator.tenantId(), operator, "TENANT_" + status, "tenant", id, tenant.getCode());
        return tenant;
    }
}
