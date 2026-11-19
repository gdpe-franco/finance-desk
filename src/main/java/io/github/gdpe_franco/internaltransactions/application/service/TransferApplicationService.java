package io.github.gdpe_franco.internaltransactions.application.service;

import io.github.gdpe_franco.internaltransactions.application.port.transfer.TransferStore;
import io.github.gdpe_franco.internaltransactions.domain.transfer.Transfer;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferApplicationService {
    public record Submission(Transfer transfer, boolean replayed) {
    }

    private final TransferRegistrationService registrationService;
    private final TransferProcessingService processingService;
    private final TransferStore transferStore;

    public TransferApplicationService(TransferRegistrationService registrationService,
                                      TransferProcessingService processingService,
                                      TransferStore transferStore) {
        this.registrationService = registrationService;
        this.processingService = processingService;
        this.transferStore = transferStore;
    }

    public Submission submit(TransferRequest request) {
        Transfer registered;
        boolean replayed;
        try {
            registered = registrationService.register(request);
            replayed = false;
        } catch (DataIntegrityViolationException exception) {
            registered = transferStore.findByIdempotencyKey(request.idempotencyKey()).orElseThrow(() -> exception);
            if (!request.sameDetails(registered)) {
                throw new IdempotencyConflictException();
            }
            replayed = true;
        }
        return new Submission(processingService.process(registered.id()), replayed);
    }

    @Transactional(readOnly = true)
    public Transfer get(long id) {
        return transferStore.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
    }
}
