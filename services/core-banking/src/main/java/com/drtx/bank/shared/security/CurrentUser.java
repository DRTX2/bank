package com.drtx.bank.shared.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/** Authenticated caller, rebuilt from the verified access token on every request. */
public record CurrentUser(UUID id, UUID tenantId, String tenantCode, String email, String name,
                          Set<String> permissions) {

    public static final UUID SYSTEM_ACTOR = new UUID(0, 0);

    public static CurrentUser from(Jwt jwt) {
        List<String> perms = jwt.getClaimAsStringList("perms");
        return new CurrentUser(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("tid")),
                jwt.getClaimAsString("tcode"), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"),
                perms == null ? Set.of() : new HashSet<>(perms));
    }

    /** Actor for jobs that run without a human (simulator, recovery sweeps). */
    public static CurrentUser system(UUID tenantId, Set<Permission> permissions) {
        Set<String> codes = new HashSet<>();
        permissions.forEach(p -> codes.add(p.code()));
        return new CurrentUser(SYSTEM_ACTOR, tenantId, null, "system@kuntur", "System", codes);
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission.code());
    }
}
