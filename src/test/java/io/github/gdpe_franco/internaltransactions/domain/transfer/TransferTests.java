package io.github.gdpe_franco.internaltransactions.domain.transfer;

import io.github.gdpe_franco.internaltransactions.domain.account.Account;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferTests {

    @Test
    void debitAndCreditPreserveTheTransferAmount() {
        var source = account(1, AccountCurrency.MXN, "1000.00", AccountStatus.ACTIVE);
        var destination = account(2, AccountCurrency.MXN, "100.00", AccountStatus.ACTIVE);

        var debited = source.debit(new BigDecimal("250.00"));
        var credited = destination.credit(new BigDecimal("250.00"));

        assertEquals(new BigDecimal("750.00"), debited.balance());
        assertEquals(new BigDecimal("350.00"), credited.balance());
        assertEquals(source.balance().add(destination.balance()), debited.balance().add(credited.balance()));
    }

    @ParameterizedTest(name = "transfer is rejected for {0}")
    @MethodSource("rejectedTransfers")
    void reportsBusinessFailure(String scenario, long sourceId, long destinationId, BigDecimal amount,
                                AccountCurrency currency, Account source, Account destination,
                                TransferFailureCode expected) {
        var transfer = Transfer.pending(new TransferRequest(UUID.randomUUID(), sourceId, destinationId, amount,
                currency));

        assertEquals(expected, transfer.failureFor(source, destination));
    }

    private static Stream<Arguments> rejectedTransfers() {
        var activeMxnSource = account(1, AccountCurrency.MXN, "100.00", AccountStatus.ACTIVE);
        var activeMxnDestination = account(2, AccountCurrency.MXN, "0.00", AccountStatus.ACTIVE);
        return Stream.of(
                Arguments.of("same account", 1L, 1L, new BigDecimal("1.00"), AccountCurrency.MXN,
                        null, null, TransferFailureCode.SAME_ACCOUNT),
                Arguments.of("nonpositive amount", 1L, 2L, BigDecimal.ZERO, AccountCurrency.MXN,
                        activeMxnSource, activeMxnDestination, TransferFailureCode.AMOUNT_NOT_POSITIVE),
                Arguments.of("missing source", 9L, 2L, new BigDecimal("1.00"), AccountCurrency.MXN,
                        null, activeMxnDestination, TransferFailureCode.SOURCE_ACCOUNT_NOT_FOUND),
                Arguments.of("missing destination", 1L, 9L, new BigDecimal("1.00"), AccountCurrency.MXN,
                        activeMxnSource, null, TransferFailureCode.DESTINATION_ACCOUNT_NOT_FOUND),
                Arguments.of("inactive source", 1L, 2L, new BigDecimal("1.00"), AccountCurrency.MXN,
                        account(1, AccountCurrency.MXN, "100.00", AccountStatus.FROZEN), activeMxnDestination,
                        TransferFailureCode.SOURCE_ACCOUNT_INACTIVE),
                Arguments.of("inactive destination", 1L, 2L, new BigDecimal("1.00"), AccountCurrency.MXN,
                        activeMxnSource, account(2, AccountCurrency.MXN, "0.00", AccountStatus.CLOSED),
                        TransferFailureCode.DESTINATION_ACCOUNT_INACTIVE),
                Arguments.of("currency mismatch", 1L, 2L, new BigDecimal("1.00"), AccountCurrency.MXN,
                        activeMxnSource, account(2, AccountCurrency.USD, "0.00", AccountStatus.ACTIVE),
                        TransferFailureCode.CURRENCY_MISMATCH),
                Arguments.of("insufficient funds", 1L, 2L, new BigDecimal("101.00"), AccountCurrency.MXN,
                        activeMxnSource, activeMxnDestination, TransferFailureCode.INSUFFICIENT_FUNDS),
                Arguments.of("destination overflow", 1L, 2L, new BigDecimal("0.01"), AccountCurrency.MXN,
                        activeMxnSource, account(2, AccountCurrency.MXN, "99999999999999999.99", AccountStatus.ACTIVE),
                        TransferFailureCode.DESTINATION_BALANCE_OVERFLOW));
    }

    private static Account account(long id, AccountCurrency currency, String balance, AccountStatus status) {
        return new Account(id, io.github.gdpe_franco.internaltransactions.domain.account.ClabeAccountNumber.generate(),
                "Test owner", currency, new BigDecimal(balance), status, 0, null, null);
    }
}
