package io.github.gdpe_franco.internaltransactions.web.controller;

import io.github.gdpe_franco.internaltransactions.application.service.AccountNotFoundException;
import io.github.gdpe_franco.internaltransactions.application.service.IdempotencyConflictException;
import io.github.gdpe_franco.internaltransactions.application.service.InvalidLedgerPageException;
import io.github.gdpe_franco.internaltransactions.application.service.TransferNotFoundException;
import io.github.gdpe_franco.internaltransactions.application.service.TransferProcessingException;
import io.github.gdpe_franco.internaltransactions.application.service.TransferRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ProblemDetail> accountNotFound(AccountNotFoundException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Account not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(TransferNotFoundException.class)
    ResponseEntity<ProblemDetail> transferNotFound(TransferNotFoundException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Transfer not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ProblemDetail> idempotencyConflict(IdempotencyConflictException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Idempotency key conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidLedgerPageException.class)
    ResponseEntity<ProblemDetail> invalidLedgerPage(InvalidLedgerPageException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid ledger page");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(TransferRegistrationException.class)
    ResponseEntity<ProblemDetail> transferRegistrationFailure(TransferRegistrationException exception) {
        log.error("Transfer registration failed", exception.getCause());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Transfer could not be registered. Retry with the same Idempotency-Key.");
        problem.setTitle("Transfer registration error");
        problem.setProperty("code", "TRANSFER_REGISTRATION_ERROR");
        return ResponseEntity.internalServerError().body(problem);
    }

    @ExceptionHandler(TransferProcessingException.class)
    ResponseEntity<ProblemDetail> transferProcessingFailure(TransferProcessingException exception) {
        log.error("Transfer {} processing failed", exception.transferId(), exception.getCause());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Transfer outcome is unknown. Retry the same request with the same Idempotency-Key.");
        problem.setTitle("Transfer processing error");
        problem.setProperty("code", "TRANSFER_PROCESSING_ERROR");
        problem.setProperty("transferId", exception.transferId());
        return ResponseEntity.internalServerError().body(problem);
    }
}
