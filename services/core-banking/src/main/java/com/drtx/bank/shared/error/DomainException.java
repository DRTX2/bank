package com.drtx.bank.shared.error;

public class DomainException extends RuntimeException {

    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static DomainException notFound(String what) {
        return new DomainException(ErrorCode.NOT_FOUND, what + " not found");
    }
}
