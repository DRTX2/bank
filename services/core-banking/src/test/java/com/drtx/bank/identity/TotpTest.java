package com.drtx.bank.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TotpTest {

    // RFC 6238 appendix B seed "12345678901234567890", base32 encoded
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesRfc6238TestVectors() {
        // The RFC lists 8 digits; authenticator apps show the last 6
        assertThat(Totp.code(RFC_SECRET, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, 1234567890L / 30)).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    void acceptsOneStepOfClockDriftAndNothingFurther() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = Totp.currentStep(now);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, 0)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, 0)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, 0)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "12345", now, 0)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, null, now, 0)).isEmpty();
    }

    @Test
    void aCodeWorksOnlyOnce() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = Totp.currentStep(now);
        String code = Totp.code(RFC_SECRET, step);
        assertThat(Totp.verify(RFC_SECRET, code, now, step)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, code, now, step - 1)).hasValue(step);
    }

    @Test
    void generatedSecretsRoundTrip() {
        String secret = Totp.newSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        Instant now = Instant.now();
        long step = Totp.currentStep(now);
        assertThat(Totp.verify(secret, Totp.code(secret, step), now, 0)).hasValue(step);
    }
}
