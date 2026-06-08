package com.docuquery.dto.response;

import com.docuquery.entity.Document;

import java.time.LocalDateTime;

/**
 * Public representation of a {@link Document} (SPEC §4.3 / §5).
 */
public record DocumentResponse(
        Long id,
        String filename,
        Integer totalChunks,
        String status,
        LocalDateTime uploadedAt
) {
    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getFilename(),
                doc.getTotalChunks(),
                doc.getStatus(),
                doc.getUploadedAt()
        );
    }
}
