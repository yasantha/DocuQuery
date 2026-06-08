package com.docuquery.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Translates exceptions into the standard {@link ErrorResponse} JSON body with the
 * correct HTTP status (SPEC §8 error catalogue).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Application errors carrying an explicit {@link ErrorCode}. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return build(ex.getCode(), ex.getMessage());
    }

    /** Bean-validation failures on {@code @Valid} request bodies (e.g. the question field). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().orElse(null);
        ErrorCode code = ErrorCode.QUESTION_BLANK;
        String message = "Validation failed";
        if (fieldError != null) {
            message = fieldError.getDefaultMessage();
            if ("Size".equals(fieldError.getCode())) {
                code = ErrorCode.QUESTION_TOO_LONG;
            }
        }
        return build(code, message);
    }

    /** Multipart upload exceeding the configured 50MB limit. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.FILE_TOO_LARGE, "File exceeds the 50MB limit");
    }

    /** Missing {@code file} part on the upload request. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return build(ErrorCode.EMPTY_FILE, "Required file part 'file' is missing");
    }

    /** Anything unforeseen → 500 without leaking internals. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code.name(), message));
    }
}
