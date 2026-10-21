package com.drtx.bank.identity;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.drtx.bank.shared.audit.AuditService;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.Permission;
import com.drtx.bank.shared.security.SystemRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private final RoleRepository roles;
    private final UserRepository users;
    private final AuditService audit;

    RoleService(RoleRepository roles, UserRepository users, AuditService audit) {
        this.roles = roles;
        this.users = users;
        this.audit = audit;
    }

    /** Institutions get every system role except PLATFORM_ADMIN, which only exists in the platform tenant. */
    @Transactional
    public void provisionSystemRoles(UUID tenantId) {
        provision(tenantId, Arrays.stream(SystemRole.values()).filter(r -> r != SystemRole.PLATFORM_ADMIN).toList());
    }

    @Transactional
    public void provisionPlatformRoles(UUID tenantId) {
        provision(tenantId, List.of(SystemRole.PLATFORM_ADMIN));
    }

    private void provision(UUID tenantId, List<SystemRole> systemRoles) {
        for (SystemRole sr : systemRoles) {
            Set<String> codes = sr.permissions().stream().map(Permission::code).collect(Collectors.toSet());
            roles.save(new Role(tenantId, sr.name(), sr.description(), true, codes));
        }
    }

    @Transactional(readOnly = true)
    public List<Role> list() {
        return roles.findAllByOrderByName();
    }

    @Transactional
    public Role create(CurrentUser actor, String name, String description, Set<String> permissions) {
        if (roles.findByName(name).isPresent()) {
            throw new DomainException(ErrorCode.CONFLICT, "A role with that name already exists");
        }
        Role role = roles.save(new Role(actor.tenantId(), name, description, false, validated(permissions)));
        audit.record(actor.tenantId(), actor, "ROLE_CREATED", "role", role.getId(), name + " " + permissions);
        return role;
    }

    @Transactional
    public Role update(CurrentUser actor, UUID id, String description, Set<String> permissions) {
        Role role = editable(id);
        role.update(description, validated(permissions));
        audit.record(actor.tenantId(), actor, "ROLE_UPDATED", "role", id, role.getName() + " " + permissions);
        return role;
    }

    @Transactional
    public void delete(CurrentUser actor, UUID id) {
        Role role = editable(id);
        if (users.existsByRole(id)) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Role is still assigned to users");
        }
        roles.delete(role);
        audit.record(actor.tenantId(), actor, "ROLE_DELETED", "role", id, role.getName());
    }

    private Role editable(UUID id) {
        Role role = roles.findById(id).orElseThrow(() -> DomainException.notFound("Role"));
        if (role.isSystem()) {
            throw new DomainException(ErrorCode.INVALID_STATE, "System roles are immutable");
        }
        return role;
    }

    /** Custom roles may only bundle tenant-scoped permissions: platform powers can never be self-granted. */
    private static Set<String> validated(Set<String> codes) {
        for (String code : codes) {
            Permission p = Permission.fromCode(code)
                    .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION, "Unknown permission " + code));
            if (p.scope() == Permission.Scope.PLATFORM) {
                throw new DomainException(ErrorCode.PRIVILEGE_ESCALATION, "Platform permissions cannot be granted");
            }
        }
        return codes;
    }
}
