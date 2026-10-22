package com.drtx.bank.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.drtx.bank.platform.Tenant;
import com.drtx.bank.platform.TenantRepository;
import com.drtx.bank.shared.audit.AuditService;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.SecurityProperties;
import com.drtx.bank.shared.tenancy.TenantScope;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    // Compared against when the user does not exist, so response time does not reveal valid emails.
    private final String dummyHash;

    private final TenantRepository tenants;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokens;
    private final PasswordEncoder encoder;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;
    private final TenantScope scope;
    private final SecurityProperties.Login loginProps;
    private final MfaService mfa;

    AuthService(TenantRepository tenants, UserRepository users, RefreshTokenRepository refreshTokens,
                TokenService tokens, PasswordEncoder encoder, LoginRateLimiter rateLimiter, AuditService audit,
                TenantScope scope, SecurityProperties props, MfaService mfa) {
        this.tenants = tenants;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.scope = scope;
        this.loginProps = props.login();
        this.mfa = mfa;
        this.dummyHash = encoder.encode(TokenService.newOpaqueToken());
    }

    public record Session(String accessToken, String refreshToken, long expiresIn, Profile user) {
    }

    public record Profile(UUID id, String email, String fullName, UUID tenantId, String tenantCode, String tenantName,
                          Set<String> roles, Set<String> permissions, boolean mfaEnabled) {
        static Profile of(User u, Tenant t) {
            return new Profile(u.getId(), u.getEmail(), u.getFullName(), t.getId(), t.getCode(), t.getName(),
                    u.roleNames(), u.permissions(), u.mfaEnabled());
        }
    }

    private sealed interface Outcome permits Ok, Failed {
    }

    private record Ok(Session session) implements Outcome {
    }

    private record Failed(ErrorCode code, String message) implements Outcome {
    }

    /** {@code otp} is only needed when the user has MFA on; without it the caller gets MFA_REQUIRED and asks. */
    public Session login(String tenantCode, String email, String password, String otp, String clientIp) {
        rateLimiter.check(clientIp);
        Tenant tenant = tenants.findByCode(tenantCode.trim().toUpperCase())
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials"));
        if (!tenant.isActive()) {
            throw new DomainException(ErrorCode.TENANT_SUSPENDED, "Institution suspended");
        }
        // Failures are returned (not thrown) so the failed-attempt counter commits.
        Outcome outcome = scope.inTenant(tenant.getId(), () -> attempt(tenant, email, password, otp, clientIp));
        return switch (outcome) {
            case Ok ok -> ok.session();
            case Failed f -> throw new DomainException(f.code(), f.message());
        };
    }

    private Outcome attempt(Tenant tenant, String email, String password, String otp, String clientIp) {
        Instant now = Instant.now();
        Optional<User> found = users.findByEmailIgnoreCase(email.trim());
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            return new Failed(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
        }
        User user = found.get();
        if (user.isLocked(now)) {
            audit.record(tenant.getId(), actor(user), "LOGIN_BLOCKED", "user", user.getId(), clientIp);
            return new Failed(ErrorCode.USER_LOCKED, "User is locked, try later or contact support");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(loginProps.lockoutThreshold(), loginProps.lockoutDuration(), now);
            audit.record(tenant.getId(), actor(user), "LOGIN_FAILED", "user", user.getId(), clientIp);
            return new Failed(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
        }
        if (user.mfaEnabled()) {
            if (otp == null || otp.isBlank()) {
                return new Failed(ErrorCode.MFA_REQUIRED, "Enter the code from your authenticator app");
            }
            if (!mfa.acceptLoginCode(user, otp.trim(), now)) {
                audit.record(tenant.getId(), actor(user), "LOGIN_FAILED", "user", user.getId(), "wrong MFA code");
                return new Failed(ErrorCode.MFA_INVALID, "Invalid or already used verification code");
            }
        }
        user.registerSuccessfulLogin();
        audit.record(tenant.getId(), actor(user), "LOGIN_SUCCEEDED", "user", user.getId(), clientIp);
        return new Ok(issue(user, tenant, UUID.randomUUID()));
    }

    /**
     * Refresh-token rotation with reuse detection (OAuth 2.0 Security BCP): presenting an already-rotated token
     * means it was stolen, so the whole token family is revoked and the user must log in again.
     */
    public Session refresh(String rawToken) {
        String hash = TokenService.sha256(rawToken);
        RefreshToken found = scope.asSystem(() -> refreshTokens.findByTokenHash(hash))
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token"));
        Outcome outcome = scope.inTenant(found.getTenantId(), () -> rotate(hash));
        return switch (outcome) {
            case Ok ok -> ok.session();
            case Failed f -> throw new DomainException(f.code(), f.message());
        };
    }

    private Outcome rotate(String hash) {
        Instant now = Instant.now();
        RefreshToken token = refreshTokens.findByTokenHash(hash).orElseThrow();
        if (token.getRevokedAt() != null) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            audit.record(token.getTenantId(), null, "REFRESH_TOKEN_REUSE_DETECTED", "user", token.getUserId(),
                    "family " + token.getFamilyId() + " revoked");
            return new Failed(ErrorCode.INVALID_REFRESH_TOKEN, "Session revoked, please log in again");
        }
        if (token.getExpiresAt().isBefore(now)) {
            return new Failed(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token expired");
        }
        User user = users.findById(token.getUserId()).orElseThrow();
        Tenant tenant = tenants.findById(token.getTenantId()).orElseThrow();
        if (user.isLocked(now) || !tenant.isActive()) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            return new Failed(ErrorCode.INVALID_REFRESH_TOKEN, "Session no longer valid");
        }
        token.revoke(now);
        return new Ok(issue(user, tenant, token.getFamilyId()));
    }

    public void logout(String rawToken) {
        String hash = TokenService.sha256(rawToken);
        scope.asSystem(() -> refreshTokens.findByTokenHash(hash)).ifPresent(t -> scope.inTenant(t.getTenantId(),
                () -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now())));
    }

    public Profile profile(CurrentUser current) {
        return scope.inTenant(current.tenantId(), () -> Profile.of(
                users.findById(current.id()).orElseThrow(() -> DomainException.notFound("User")),
                tenants.findById(current.tenantId()).orElseThrow()));
    }

    private Session issue(User user, Tenant tenant, UUID familyId) {
        String raw = TokenService.newOpaqueToken();
        refreshTokens.save(new RefreshToken(tenant.getId(), user.getId(), TokenService.sha256(raw), familyId,
                tokens.refreshExpiry()));
        return new Session(tokens.accessToken(user, tenant), raw, tokens.accessTokenTtlSeconds(),
                Profile.of(user, tenant));
    }

    private static CurrentUser actor(User u) {
        return new CurrentUser(u.getId(), u.getTenantId(), null, u.getEmail(), u.getFullName(), Set.of());
    }
}
