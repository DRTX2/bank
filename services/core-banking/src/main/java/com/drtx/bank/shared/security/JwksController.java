package com.drtx.bank.shared.security;

import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public verification keys so other services (or an API gateway) can validate Kuntur tokens offline. */
@RestController
class JwksController {

    private final JWKSet publicKeys;

    JwksController(RSAKey signingKey) {
        this.publicKeys = new JWKSet(signingKey.toPublicJWK());
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return publicKeys.toJSONObject();
    }
}
