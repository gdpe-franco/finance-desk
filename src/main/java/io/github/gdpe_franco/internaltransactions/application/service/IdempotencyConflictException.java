package io.github.gdpe_franco.internaltransactions.application.service;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency-Key was already used with different transfer details");
    }
}
