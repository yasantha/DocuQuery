package com.docuquery.exception;

import java.time.LocalDateTime;

/**
 * Standard JSON error body returned for every handled error (SPEC §4.3 / §8).
 */
public record ErrorResponse(
        String error,
        String message,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, LocalDateTime.now());
    }
}
