package com.drtx.bank.identity;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.drtx.bank.shared.audit.AuditService;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.Permission;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    UserService(UserRepository users, RoleRepository roles, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.audit = audit;
    }

    /** Low-level creation used by provisioning, onboarding and admins. Runs in the caller's tenant transaction. */
    @Transactional
    public User createUser(UUID tenantId, String email, String fullName, String nationalId, String rawPassword,
                           Collection<String> roleNames) {
        PasswordPolicy.check(rawPassword);
        String normalized = email.trim().toLowerCase();
        if (users.findByEmailIgnoreCase(normalized).isPresent()) {
            throw new DomainException(ErrorCode.CONFLICT, "Email already registered");
        }
        return users.save(new User(tenantId, normalized, fullName, nationalId, encoder.encode(rawPassword),
                resolveRoles(roleNames)));
    }

    /** Demo seeding only: MFA already active with a known secret. */
    @Transactional
    public void enableDemoMfa(User user, String secret) {
        user.startMfaEnrollment(secret);
        user.enableMfa(java.time.Instant.now());
        users.save(user);
    }

    @Transactional
    public User create(CurrentUser actor, String email, String fullName, String nationalId, String rawPassword,
                       Collection<String> roleNames) {
        User user = createUser(actor.tenantId(), email, fullName, nationalId, rawPassword, roleNames);
        audit.record(actor.tenantId(), actor, "USER_CREATED", "user", user.getId(), email + " " + roleNames);
        return user;
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return users.findAllByOrderByFullName();
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> DomainException.notFound("User"));
    }

    /**
     * Role assignment rules: nobody edits their own roles (no self-escalation, no accidental lock-out), and
     * platform-scoped roles can only be handed out by someone who already holds platform powers.
     */
    @Transactional
    public User assignRoles(CurrentUser actor, UUID userId, Collection<String> roleNames) {
        if (actor.id().equals(userId)) {
            throw new DomainException(ErrorCode.SEGREGATION_OF_DUTIES, "You cannot change your own roles");
        }
        User user = get(userId);
        Set<Role> newRoles = resolveRoles(roleNames);
        boolean grantsPlatform = newRoles.stream().flatMap(r -> r.getPermissions().stream())
                .anyMatch(p -> Permission.fromCode(p).map(x -> x.scope() == Permission.Scope.PLATFORM).orElse(false));
        if (grantsPlatform && !actor.has(Permission.TENANTS_MANAGE)) {
            throw new DomainException(ErrorCode.PRIVILEGE_ESCALATION, "Only platform operators grant platform roles");
        }
        Set<String> before = user.roleNames();
        user.replaceRoles(newRoles);
        audit.record(actor.tenantId(), actor, "USER_ROLES_CHANGED", "user", userId, before + " -> " + roleNames);
        return user;
    }

    @Transactional
    public User changeStatus(CurrentUser actor, UUID userId, User.Status status) {
        if (actor.id().equals(userId)) {
            throw new DomainException(ErrorCode.SEGREGATION_OF_DUTIES, "You cannot change your own status");
        }
        User user = get(userId);
        user.changeStatus(status);
        audit.record(actor.tenantId(), actor, "USER_" + status, "user", userId, user.getEmail());
        return user;
    }

    private Set<Role> resolveRoles(Collection<String> names) {
        Set<Role> resolved = new HashSet<>();
        for (String name : names) {
            resolved.add(roles.findByName(name)
                    .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION, "Unknown role " + name)));
        }
        if (resolved.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION, "At least one role is required");
        }
        return resolved;
    }
}
