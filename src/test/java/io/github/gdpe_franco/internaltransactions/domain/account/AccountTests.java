package io.github.gdpe_franco.internaltransactions.domain.account;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void rejectsInvalidAccountValues() {
        var validClabe = ClabeAccountNumber.generate();

        assertThrows(IllegalArgumentException.class, () -> new Account(null, validClabe, " ", AccountCurrency.MXN,
                BigDecimal.ZERO, AccountStatus.ACTIVE, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Account(null, validClabe, "Ada", AccountCurrency.MXN,
                new BigDecimal("-0.01"), AccountStatus.ACTIVE, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Account(null, validClabe, "Ada", AccountCurrency.MXN,
                new BigDecimal("1.001"), AccountStatus.ACTIVE, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Account(null, "000000000000000001", "Ada",
                AccountCurrency.MXN, BigDecimal.ZERO, AccountStatus.ACTIVE, 0, null, null));
    }

    @Test
    void clabeCheckDigitRejectsTampering() {
        var clabe = ClabeAccountNumber.generate();
        char changedDigit = clabe.charAt(17) == '9' ? '0' : (char) (clabe.charAt(17) + 1);

        assertTrue(ClabeAccountNumber.isValid(clabe));
        assertTrue(!ClabeAccountNumber.isValid(clabe.substring(0, 17) + changedDigit));
    }
}
