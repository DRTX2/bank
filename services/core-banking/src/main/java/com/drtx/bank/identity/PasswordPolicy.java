package com.drtx.bank.identity;

import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;

/** NIST 800-63B inspired: length over complexity, plus a minimal character mix and a deny-list. */
final class PasswordPolicy {

    private static final java.util.Set<String> COMMON = java.util.Set.of("password123", "1234567890", "qwerty12345",
            "contrasena123", "kuntur12345");

    private PasswordPolicy() {
    }

    static void check(String password) {
        if (password == null || password.length() < 10 || password.length() > 128) {
            throw new DomainException(ErrorCode.WEAK_PASSWORD, "Password must be 10-128 characters");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit || COMMON.contains(password.toLowerCase())) {
            throw new DomainException(ErrorCode.WEAK_PASSWORD, "Password must mix letters and digits and not be common");
        }
    }
}
