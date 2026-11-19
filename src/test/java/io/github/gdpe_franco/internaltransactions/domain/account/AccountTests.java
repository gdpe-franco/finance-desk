package io.github.gdpe_franco.internaltransactions.domain.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTests {

    @Test
    void opensAnActiveZeroBalanceAccountWithValidSyntheticClabe() {
        var account = Account.open("  Ada Lovelace  ", AccountCurrency.MXN);

        assertEquals("Ada Lovelace", account.ownerName());
        assertEquals(new BigDecimal("0.00"), account.balance());
        assertEquals(AccountStatus.ACTIVE, account.status());
        assertTrue(ClabeAccountNumber.isValid(account.accountNumber()));
        assertTrue(account.accountNumber().startsWith("999999"));
    }

    @ParameterizedTest(name = "rejects invalid account: {0}")
    @MethodSource("invalidAccounts")
    void rejectsInvalidAccountValues(String scenario, String accountNumber, String ownerName, BigDecimal balance) {
        assertThrows(IllegalArgumentException.class, () -> new Account(null, accountNumber, ownerName,
                AccountCurrency.MXN, balance, AccountStatus.ACTIVE, 0, null, null));
    }

    @ParameterizedTest(name = "rejects invalid updated owner name: {0}")
    @MethodSource("invalidOwnerNames")
    void rejectsInvalidUpdatedOwnerNames(String scenario, String ownerName) {
        var account = Account.open("Ada", AccountCurrency.MXN);

        assertThrows(IllegalArgumentException.class, () -> account.withOwnerName(ownerName));
    }

    private static Stream<Arguments> invalidAccounts() {
        var validClabe = ClabeAccountNumber.generate();

        return Stream.of(
                Arguments.of("blank owner", validClabe, " ", BigDecimal.ZERO),
                Arguments.of("negative balance", validClabe, "Ada", new BigDecimal("-0.01")),
                Arguments.of("excess balance precision", validClabe, "Ada", new BigDecimal("1.001")),
                Arguments.of("invalid CLABE check digit", "000000000000000001", "Ada", BigDecimal.ZERO));
    }

    private static Stream<Arguments> invalidOwnerNames() {
        return Stream.of(
                Arguments.of("null", null),
                Arguments.of("blank", "   "),
                Arguments.of("over 120 characters", "A".repeat(121)));
    }

    @Test
    void clabeCheckDigitRejectsTampering() {
        var clabe = ClabeAccountNumber.generate();
        char changedDigit = clabe.charAt(17) == '9' ? '0' : (char) (clabe.charAt(17) + 1);

        assertTrue(ClabeAccountNumber.isValid(clabe));
        assertFalse(ClabeAccountNumber.isValid(clabe.substring(0, 17) + changedDigit));
    }
}
