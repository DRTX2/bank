package com.drtx.bank.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.drtx.bank.shared.security.SecurityProperties;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Seals the TOTP seed with AES-256-GCM before it reaches the database, so a leaked dump or backup does not hand out
 * second factors. Stored as {@code v1:} + base64(iv || ciphertext); the prefix leaves room for key rotation.
 */
// shortcut: one app-level key from env/Secrets Manager, no rotation and no per-row AAD; use KMS data keys (key id in the prefix) when rotation is required.
@Converter
@Component
class MfaSeedConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    MfaSeedConverter(SecurityProperties props) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(props.mfa().seedKey());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("bank.security.mfa.seed-key must be base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("bank.security.mfa.seed-key must decode to 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String seed) {
        if (seed == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(seed.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length)
                    .put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot seal the TOTP seed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored; // rows written before V3 are plain text
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot open the TOTP seed: wrong key or tampered data", e);
        }
    }
}
