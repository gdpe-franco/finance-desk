package io.github.gdpe_franco.internaltransactions.application.service;

public class InvalidLedgerPageException extends RuntimeException {
    public InvalidLedgerPageException() {
        super("Ledger page must be nonnegative and size must be between 1 and 100");
    }
}
