package io.github.gdpe_franco.internaltransactions.application.service;

import io.github.gdpe_franco.internaltransactions.application.port.transfer.TransferStore;
import io.github.gdpe_franco.internaltransactions.domain.transfer.Transfer;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferRegistrationService {
    private final TransferStore transferStore;

    public TransferRegistrationService(TransferStore transferStore) {
        this.transferStore = transferStore;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transfer register(TransferRequest request) {
        return transferStore.save(Transfer.pending(request));
    }
}
