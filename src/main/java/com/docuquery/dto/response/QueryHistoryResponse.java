package com.docuquery.dto.response;

import com.docuquery.entity.QueryHistory;

import java.time.LocalDateTime;

/**
 * Item in {@code GET /documents/{id}/query/history} (SPEC §5).
 */
public record QueryHistoryResponse(
        Long id,
        String question,
        String answer,
        Integer chunksUsed,
        LocalDateTime queriedAt
) {
    public static QueryHistoryResponse from(QueryHistory history) {
        return new QueryHistoryResponse(
                history.getId(),
                history.getQuestion(),
                history.getAnswer(),
                history.getChunksUsed(),
                history.getQueriedAt()
        );
    }
}
