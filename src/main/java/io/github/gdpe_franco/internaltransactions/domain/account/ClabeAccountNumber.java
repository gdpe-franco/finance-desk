package io.github.gdpe_franco.internaltransactions.domain.account;

import java.security.SecureRandom;
import java.util.Locale;

public final class ClabeAccountNumber {
    private static final String SYNTHETIC_INSTITUTION_AND_BRANCH = "999999";
    private static final long ACCOUNT_NUMBER_RANGE = 100_000_000_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ClabeAccountNumber() {
    }

    public static String generate() {
        var firstSeventeenDigits = SYNTHETIC_INSTITUTION_AND_BRANCH
                + String.format(Locale.ROOT, "%011d", RANDOM.nextLong(ACCOUNT_NUMBER_RANGE));
        return firstSeventeenDigits + checkDigit(firstSeventeenDigits);
    }

    public static boolean isValid(String value) {
        if (value == null || !value.matches("\\d{18}")) {
            return false;
        }
        return value.charAt(17) == (char) ('0' + checkDigit(value.substring(0, 17)));
    }

    private static int checkDigit(String firstSeventeenDigits) {
        int sum = 0;
        for (int i = 0; i < firstSeventeenDigits.length(); i++) {
            int weight = switch (i % 3) {
                case 0 -> 3;
                case 1 -> 7;
                default -> 1;
            };
            sum += (firstSeventeenDigits.charAt(i) - '0') * weight % 10;
        }
        return (10 - sum % 10) % 10;
    }
}
