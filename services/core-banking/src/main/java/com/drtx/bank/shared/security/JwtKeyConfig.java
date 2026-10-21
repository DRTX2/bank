package com.drtx.bank.shared.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

/** RS256 signing keys. In production they come from AWS Secrets Manager via env vars. */
@Configuration(proxyBeanMethods = false)
class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    RSAKey jwtSigningKey(SecurityProperties props) throws Exception {
        SecurityProperties.Jwt jwt = props.jwt();
        KeyPair pair;
        if (StringUtils.hasText(jwt.privateKey()) && StringUtils.hasText(jwt.publicKey())) {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            pair = new KeyPair(kf.generatePublic(new X509EncodedKeySpec(pem(jwt.publicKey()))),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(pem(jwt.privateKey()))));
        } else {
            log.warn("No JWT key pair configured: generating an ephemeral one. Never do this in production.");
            pair = generate();
        }
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("kuntur-" + Integer.toHexString(pair.getPublic().hashCode()))
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey key, SecurityProperties props) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.jwt().issuer())));
        return decoder;
    }

    private static byte[] pem(String pem) {
        String body = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static KeyPair generate() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
