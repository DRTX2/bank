package com.drtx.bank.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class AccountNumbersTest {

    @RepeatedTest(50)
    void generatedNumbersPassTheirOwnCheckDigits() {
        String iban = AccountNumbers.generate("EC", "ANDN");
        assertThat(iban).startsWith("EC").hasSize(22).matches("EC\\d{2}ANDN\\d{14}");
        assertThat(AccountNumbers.isValid(iban)).isTrue();
    }

    @Test
    void validatesRealWorldIbans() {
        assertThat(AccountNumbers.isValid("DE89 3704 0044 0532 0130 00")).isTrue();
        assertThat(AccountNumbers.isValid("GB82WEST12345698765432")).isTrue();
    }

    @Test
    void detectsTyposAndTranspositions() {
        String iban = AccountNumbers.generate("EC", "ANDN");
        char[] chars = iban.toCharArray();
        int i = 10;
        char tmp = chars[i];
        chars[i] = chars[i + 1];
        chars[i + 1] = tmp;
        String transposed = new String(chars);
        if (!transposed.equals(iban)) {
            assertThat(AccountNumbers.isValid(transposed)).isFalse();
        }
        assertThat(AccountNumbers.isValid("DE88370400440532013000")).isFalse();
        assertThat(AccountNumbers.isValid("garbage")).isFalse();
        assertThat(AccountNumbers.isValid(null)).isFalse();
    }

    @Test
    void masksHolderNames() {
        assertThat(AccountService.mask("Ana Morales")).isEqualTo("A** M******");
    }
}
