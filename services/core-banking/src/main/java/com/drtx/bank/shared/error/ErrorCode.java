package com.drtx.bank.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Business error catalogue. Payment failures carry their ISO 20022 ExternalStatusReason1Code so the same
 * code surfaces in REST problem details, pain.002 reports and Kafka events.
 */
public enum ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    VALIDATION(HttpStatus.BAD_REQUEST),
    CONFLICT(HttpStatus.CONFLICT),
    INVALID_STATE(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
    MFA_REQUIRED(HttpStatus.UNAUTHORIZED),
    MFA_INVALID(HttpStatus.UNAUTHORIZED),
    MFA_NOT_ENROLLED(HttpStatus.FORBIDDEN),
    // 403, not 401: a wrong step-up code must not look like an expired session to the client
    STEP_UP_FAILED(HttpStatus.FORBIDDEN),
    USER_LOCKED(HttpStatus.LOCKED),
    TENANT_SUSPENDED(HttpStatus.FORBIDDEN),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    IDEMPOTENCY_CONFLICT(HttpStatus.UNPROCESSABLE_CONTENT),
    SEGREGATION_OF_DUTIES(HttpStatus.FORBIDDEN),
    PRIVILEGE_ESCALATION(HttpStatus.FORBIDDEN),
    WEAK_PASSWORD(HttpStatus.BAD_REQUEST),

    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT, "AM04"),
    LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "AM02"),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "AM03"),
    INVALID_ACCOUNT_NUMBER(HttpStatus.UNPROCESSABLE_CONTENT, "AC01"),
    ACCOUNT_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT, "AC04"),
    ACCOUNT_BLOCKED(HttpStatus.UNPROCESSABLE_CONTENT, "AC06"),
    FRAUD_SUSPECTED(HttpStatus.UNPROCESSABLE_CONTENT, "FRAD"),
    DUPLICATE_MESSAGE(HttpStatus.CONFLICT, "DUPL"),
    INVALID_MESSAGE(HttpStatus.BAD_REQUEST, "FF01");

    private final HttpStatus status;
    private final String isoReason;

    ErrorCode(HttpStatus status) {
        this(status, null);
    }

    ErrorCode(HttpStatus status, String isoReason) {
        this.status = status;
        this.isoReason = isoReason;
    }

    public HttpStatus status() {
        return status;
    }

    /** ISO 20022 reason code, or null for non-payment errors. */
    public String isoReason() {
        return isoReason;
    }
}
