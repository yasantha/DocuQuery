package com.docuquery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /documents/{id}/query} (SPEC §5 / §4.3).
 */
public record QueryRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 1000, message = "question must not exceed 1000 characters")
        String question
) {
}
