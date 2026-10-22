package com.drtx.bank.identity;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 time-based one-time passwords (HMAC-SHA1, 6 digits, 30 s steps): the variant every authenticator app
 * supports. Implemented on the JDK instead of a library because it is 40 lines and sits on the login path.
 */
public final class Totp {

    static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** 160-bit secret, base32 encoded (the size RFC 4226 recommends for SHA-1). */
    static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        return out.toString();
    }

    public static long currentStep(Instant now) {
        return now.getEpochSecond() / STEP_SECONDS;
    }

    public static String code(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decode(base32Secret), "HmacSHA1"));
            byte[] h = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(step).array());
            int offset = h[h.length - 1] & 0xf;
            int binary = ((h[offset] & 0x7f) << 24) | ((h[offset + 1] & 0xff) << 16)
                    | ((h[offset + 2] & 0xff) << 8) | (h[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Accepts the current step and one on either side (clock drift), but only steps after {@code lastUsedStep}:
     * a code works once, so a shoulder-surfed or phished code cannot be replayed.
     */
    static OptionalLong verify(String base32Secret, String candidate, Instant now, long lastUsedStep) {
        if (candidate == null || !candidate.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = currentStep(now);
        for (long step = current - 1; step <= current + 1; step++) {
            boolean match = MessageDigest.isEqual(code(base32Secret, step).getBytes(), candidate.getBytes());
            if (match && step > lastUsedStep) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    private static byte[] decode(String base32) {
        String s = base32.replace("=", "").replace(" ", "").toUpperCase();
        byte[] out = new byte[s.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int i = 0;
        for (char c : s.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Invalid base32 secret");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out[i++] = (byte) (buffer >> (bits - 8));
                bits -= 8;
            }
        }
        return out;
    }
}
