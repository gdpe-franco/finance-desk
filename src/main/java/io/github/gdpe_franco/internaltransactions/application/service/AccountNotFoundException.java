package io.github.gdpe_franco.internaltransactions.application.service;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(long id) {
        super("Account %d was not found".formatted(id));
    }
}
