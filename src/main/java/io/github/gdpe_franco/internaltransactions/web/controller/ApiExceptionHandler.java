package io.github.gdpe_franco.internaltransactions.web.controller;

import io.github.gdpe_franco.internaltransactions.application.service.AccountNotFoundException;
import io.github.gdpe_franco.internaltransactions.application.service.IdempotencyConflictException;
import io.github.gdpe_franco.internaltransactions.application.service.InvalidLedgerPageException;
import io.github.gdpe_franco.internaltransactions.application.service.TransferNotFoundException;
import io.github.gdpe_franco.internaltransactions.application.service.TransferProcessingException;
import io.github.gdpe_franco.internaltransactions.application.service.TransferRegistrationException;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problem.setTitle("Validation failed");
        var errors = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

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
        var retryable = hasRetryableLockFailure(exception.getCause());
        var status = retryable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.INTERNAL_SERVER_ERROR;
        var code = retryable ? "RETRYABLE_CONCURRENCY_FAILURE" : "TRANSFER_PROCESSING_ERROR";
        var problem = ProblemDetail.forStatusAndDetail(status,
                "Transfer outcome is unknown. Retry the same request with the same Idempotency-Key.");
        problem.setTitle("Transfer processing error");
        problem.setProperty("code", code);
        problem.setProperty("transferId", exception.transferId());
        return ResponseEntity.status(status).body(problem);
    }

    private boolean hasRetryableLockFailure(Throwable cause) {
        for (var current = cause; current != null; current = current.getCause()) {
            if (current instanceof PessimisticLockingFailureException
                    || current instanceof PessimisticLockException
                    || current instanceof LockTimeoutException
                    || current instanceof QueryTimeoutException
                    || current instanceof java.sql.SQLTransactionRollbackException) {
                return true;
            }
        }
        return false;
    }
}
