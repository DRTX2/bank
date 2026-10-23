package com.drtx.bank.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.drtx.bank.shared.tenancy.TenantOwned;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends TenantOwned {

    public enum Status { ACTIVE, LOCKED, DISABLED }

    @Id
    private UUID id;
    private String email;
    private String fullName;
    private String nationalId;
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    private Status status;
    private int failedLogins;
    private Instant lockedUntil;
    private Instant createdAt;
    @Convert(converter = MfaSeedConverter.class)
    private String mfaSecret;
    private Instant mfaEnabledAt;
    private long mfaLastStep;
    @Version
    private long version;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public User(UUID tenantId, String email, String fullName, String nationalId, String passwordHash, Set<Role> roles) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.email = email;
        this.fullName = fullName;
        this.nationalId = nationalId;
        this.passwordHash = passwordHash;
        this.status = Status.ACTIVE;
        this.roles = new HashSet<>(roles);
        this.createdAt = Instant.now();
    }

    public Set<String> permissions() {
        return roles.stream().flatMap(r -> r.getPermissions().stream()).collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    public Set<String> roleNames() {
        return roles.stream().map(Role::getName).collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    public boolean isLocked(Instant now) {
        return status == Status.DISABLED || status == Status.LOCKED
                || (lockedUntil != null && lockedUntil.isAfter(now));
    }

    void registerFailedLogin(int threshold, Duration lockout, Instant now) {
        failedLogins++;
        if (failedLogins >= threshold) {
            lockedUntil = now.plus(lockout);
            failedLogins = 0;
        }
    }

    void registerSuccessfulLogin() {
        failedLogins = 0;
        lockedUntil = null;
    }

    public boolean mfaEnabled() {
        return mfaEnabledAt != null;
    }

    void startMfaEnrollment(String secret) {
        mfaSecret = secret;
        mfaEnabledAt = null;
        mfaLastStep = 0;
    }

    void enableMfa(Instant now) {
        mfaEnabledAt = now;
    }

    void disableMfa() {
        mfaSecret = null;
        mfaEnabledAt = null;
        mfaLastStep = 0;
    }

    /** Consumes a code: true only for a valid code newer than the last one used. */
    boolean acceptOtp(String code, Instant now) {
        if (mfaSecret == null) {
            return false;
        }
        var step = Totp.verify(mfaSecret, code, now, mfaLastStep);
        step.ifPresent(s -> mfaLastStep = s);
        return step.isPresent();
    }

    void replaceRoles(Set<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }

    void changeStatus(Status status) {
        this.status = status;
        if (status == Status.ACTIVE) {
            lockedUntil = null;
            failedLogins = 0;
        }
    }
}
