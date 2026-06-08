package com.docuquery.exception;

import org.springframework.http.HttpStatus;

/**
 * Application error catalogue (SPEC §8). Each code maps to a fixed HTTP status
 * and is surfaced in the JSON error response as the {@code error} field.
 */
public enum ErrorCode {

    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    EMPTY_FILE(HttpStatus.BAD_REQUEST),
    DOCUMENT_NOT_READY(HttpStatus.CONFLICT),
    QUESTION_BLANK(HttpStatus.BAD_REQUEST),
    QUESTION_TOO_LONG(HttpStatus.BAD_REQUEST),
    PDF_EXTRACTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    EMBEDDING_API_ERROR(HttpStatus.BAD_GATEWAY),
    GENERATION_API_ERROR(HttpStatus.BAD_GATEWAY);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
