package com.docuquery.exception;

import lombok.Getter;

/**
 * Carries an {@link ErrorCode} (and thus an HTTP status) up to the
 * global exception handler, which renders the standard JSON error body.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
