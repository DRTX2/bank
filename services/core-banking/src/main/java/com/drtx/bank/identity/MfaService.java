package com.drtx.bank.identity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.drtx.bank.shared.audit.AuditService;
import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.SecurityProperties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TOTP second factor: enrollment, login challenge and step-up for sensitive operations. Wrong codes count
 * towards the same lockout as wrong passwords, so the 6-digit space cannot be brute-forced.
 */
@Service
public class MfaService {

    private final UserRepository users;
    private final AuditService audit;
    private final SecurityProperties.Login loginProps;

    MfaService(UserRepository users, AuditService audit, SecurityProperties props) {
        this.users = users;
        this.audit = audit;
        this.loginProps = props.login();
    }

    public record Enrollment(String secret, String otpauthUri) {
    }

    @Transactional
    public Enrollment enroll(CurrentUser current) {
        User user = load(current);
        if (user.mfaEnabled()) {
            throw new DomainException(ErrorCode.INVALID_STATE, "MFA is already active, disable it first");
        }
        String secret = Totp.newSecret();
        user.startMfaEnrollment(secret);
        String label = URLEncoder.encode("Kuntur:" + user.getEmail(), StandardCharsets.UTF_8);
        return new Enrollment(secret, "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=Kuntur&algorithm=SHA1&digits=6&period=" + Totp.STEP_SECONDS);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public void activate(CurrentUser current, String code) {
        User user = load(current);
        if (user.getMfaSecret() == null || user.mfaEnabled()) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Start the enrollment first");
        }
        Instant now = Instant.now();
        consume(user, code, now, ErrorCode.MFA_INVALID);
        user.enableMfa(now);
        audit.record(current.tenantId(), current, "MFA_ENABLED", "user", user.getId(), null);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public void disable(CurrentUser current, String code) {
        User user = load(current);
        if (!user.mfaEnabled()) {
            throw new DomainException(ErrorCode.INVALID_STATE, "MFA is not active");
        }
        consume(user, code, Instant.now(), ErrorCode.MFA_INVALID);
        user.disableMfa();
        audit.record(current.tenantId(), current, "MFA_DISABLED", "user", user.getId(), null);
    }

    /**
     * Step-up authentication: a fresh code proves the person, not just a stolen access token, is at the keyboard.
     * Runs in its own transaction so a used code stays used even if the guarded operation then fails.
     */
    @Transactional(noRollbackFor = DomainException.class)
    public void requireStepUp(CurrentUser current, String code) {
        User user = load(current);
        if (!user.mfaEnabled()) {
            throw new DomainException(ErrorCode.MFA_NOT_ENROLLED,
                    "This operation requires two-factor authentication. Enable it under Seguridad.");
        }
        consume(user, code, Instant.now(), ErrorCode.STEP_UP_FAILED);
    }

    /** Login second step. Caller already checked the password and runs inside a tenant transaction. */
    boolean acceptLoginCode(User user, String code, Instant now) {
        if (user.acceptOtp(code, now)) {
            return true;
        }
        user.registerFailedLogin(loginProps.lockoutThreshold(), loginProps.lockoutDuration(), now);
        return false;
    }

    private void consume(User user, String code, Instant now, ErrorCode onFailure) {
        if (user.isLocked(now)) {
            throw new DomainException(ErrorCode.USER_LOCKED, "User is locked, try later or contact support");
        }
        if (!acceptLoginCode(user, code, now)) {
            throw new DomainException(onFailure, "Invalid or already used verification code");
        }
    }

    private User load(CurrentUser current) {
        return users.findById(current.id()).orElseThrow(() -> DomainException.notFound("User"));
    }
}
