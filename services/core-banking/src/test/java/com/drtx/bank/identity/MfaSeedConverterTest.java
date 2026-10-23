package com.drtx.bank.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import com.drtx.bank.shared.security.SecurityProperties;

import org.junit.jupiter.api.Test;

class MfaSeedConverterTest {

    private static final String SEED = "KUNTURDEMOGERENTEMFAANDINAKUNTUR";

    private static MfaSeedConverter converter(byte fill) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, fill);
        return new MfaSeedConverter(new SecurityProperties(null, null, null,
                new SecurityProperties.Mfa(Base64.getEncoder().encodeToString(key))));
    }

    @Test
    void sealsAndOpensTheSeedWithoutLeakingItInTheStoredValue() {
        MfaSeedConverter c = converter((byte) 1);
        String stored = c.convertToDatabaseColumn(SEED);

        assertThat(stored).startsWith("v1:").doesNotContain(SEED).hasSizeLessThanOrEqualTo(255);
        assertThat(c.convertToEntityAttribute(stored)).isEqualTo(SEED);
        // a fresh IV per write: the same seed never produces the same ciphertext
        assertThat(c.convertToDatabaseColumn(SEED)).isNotEqualTo(stored);
    }

    @Test
    void keepsNullsAndStillReadsRowsWrittenBeforeSealing() {
        MfaSeedConverter c = converter((byte) 1);
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToEntityAttribute(null)).isNull();
        assertThat(c.convertToEntityAttribute(SEED)).isEqualTo(SEED);
    }

    @Test
    void refusesTheWrongKeyAndTamperedData() {
        String stored = converter((byte) 1).convertToDatabaseColumn(SEED);
        assertThatThrownBy(() -> converter((byte) 2).convertToEntityAttribute(stored))
                .isInstanceOf(IllegalStateException.class);
        String tampered = stored.substring(0, stored.length() - 4) + (stored.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThatThrownBy(() -> converter((byte) 1).convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeysThatAreNotAes256() {
        assertThatThrownBy(() -> new MfaSeedConverter(new SecurityProperties(null, null, null,
                new SecurityProperties.Mfa(Base64.getEncoder().encodeToString(new byte[16])))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new MfaSeedConverter(new SecurityProperties(null, null, null,
                new SecurityProperties.Mfa("not base64!"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("base64");
    }
}
