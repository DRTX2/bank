package com.drtx.bank.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.drtx.bank.platform.Tenant;
import com.drtx.bank.shared.security.SecurityProperties;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder encoder;
    private final SecurityProperties.Jwt props;

    TokenService(JwtEncoder encoder, SecurityProperties props) {
        this.encoder = encoder;
        this.props = props.jwt();
    }

    /**
     * Short-lived (10 min) access token carrying the effective permissions, so authorization is stateless.
     * Trade-off: a revoked role keeps working until the token expires; refresh re-reads roles from the DB.
     */
    String accessToken(User user, Tenant tenant) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .claim("tid", tenant.getId().toString())
                .claim("tcode", tenant.getCode())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("roles", new ArrayList<>(user.roleNames()))
                .claim("perms", new ArrayList<>(user.permissions()))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    long accessTokenTtlSeconds() {
        return props.accessTokenTtl().toSeconds();
    }

    Instant refreshExpiry() {
        return Instant.now().plus(props.refreshTokenTtl());
    }

    static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
