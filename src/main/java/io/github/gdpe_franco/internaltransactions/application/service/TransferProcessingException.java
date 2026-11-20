package io.github.gdpe_franco.internaltransactions.application.service;

public class TransferProcessingException extends RuntimeException {
    private final long transferId;

    public TransferProcessingException(long transferId, Throwable cause) {
        super("Transfer processing failed", cause);
        this.transferId = transferId;
    }

    public long transferId() {
        return transferId;
    }
}
