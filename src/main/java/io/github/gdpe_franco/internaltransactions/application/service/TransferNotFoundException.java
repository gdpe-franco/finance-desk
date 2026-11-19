package io.github.gdpe_franco.internaltransactions.application.service;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(long id) {
        super("Transfer %d was not found".formatted(id));
    }
}
