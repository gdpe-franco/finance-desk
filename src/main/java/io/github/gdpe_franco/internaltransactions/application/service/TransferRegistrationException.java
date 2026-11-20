package io.github.gdpe_franco.internaltransactions.application.service;

public class TransferRegistrationException extends RuntimeException {
    public TransferRegistrationException(Throwable cause) {
        super("Transfer request could not be registered", cause);
    }
}
