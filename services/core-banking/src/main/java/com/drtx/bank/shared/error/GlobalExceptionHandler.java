package com.drtx.bank.shared.error;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** RFC 9457 problem details for every error; internals never leak to the client. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException ex) {
        ProblemDetail pd = problem(ex.code().status(), ex.code().name(), ex.getMessage());
        if (ex.code().isoReason() != null) {
            pd.setProperty("isoReason", ex.code().isoReason());
        }
        return pd;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.name(), "You do not have permission to perform this action");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimistic(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, ErrorCode.CONFLICT.name(), "The resource was modified concurrently, retry");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex) {
        log.warn("Integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, ErrorCode.CONFLICT.name(), "The request conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Unexpected error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()), (a, b) -> a));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION.name(), "Request validation failed");
        pd.setProperty("fields", fields);
        return ResponseEntity.badRequest().body(pd);
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://kuntur.dev/problems/" + code.toLowerCase().replace('_', '-')));
        pd.setTitle(code);
        pd.setProperty("code", code);
        return pd;
    }
}
