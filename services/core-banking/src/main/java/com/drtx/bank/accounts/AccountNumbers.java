package com.drtx.bank.accounts;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * IBAN-structured account identifiers (ISO 13616) with ISO 7064 MOD 97-10 check digits:
 * {@code CC kk BBBB NNNNNNNNNNNNNN} where BBBB is the institution code. Catches typos and transpositions
 * before money moves (rejected with ISO reason AC01).
 */
public final class AccountNumbers {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private AccountNumbers() {
    }

    public static String generate(String country, String bankCode) {
        StringBuilder digits = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            digits.append(RANDOM.nextInt(10));
        }
        String bban = bankCode + digits;
        int check = 98 - mod97(bban + country + "00");
        return country + String.format("%02d", check) + bban;
    }

    public static boolean isValid(String iban) {
        if (iban == null) {
            return false;
        }
        String s = iban.replace(" ", "").toUpperCase();
        if (!s.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{8,30}")) {
            return false;
        }
        return mod97(s.substring(4) + s.substring(0, 4)) == 1;
    }

    public static String normalize(String iban) {
        return iban == null ? null : iban.replace(" ", "").toUpperCase();
    }

    private static int mod97(String s) {
        StringBuilder numeric = new StringBuilder();
        for (char c : s.toCharArray()) {
            numeric.append(Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf(c));
        }
        return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).intValue();
    }
}
