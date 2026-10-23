package com.drtx.bank.shared.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bank.security")
public record SecurityProperties(Jwt jwt, List<String> corsOrigins, Login login, Mfa mfa) {

    public record Jwt(String issuer, Duration accessTokenTtl, Duration refreshTokenTtl, String privateKey,
                      String publicKey) {
    }

    public record Mfa(String seedKey) {
    }

    public record Login(int maxAttemptsPerMinute, int lockoutThreshold, Duration lockoutDuration) {
    }
}
