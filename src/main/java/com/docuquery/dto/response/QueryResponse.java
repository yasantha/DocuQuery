package com.docuquery.dto.response;

import java.util.List;

/**
 * Response of {@code POST /documents/{id}/query} (SPEC §4.3 / §5).
 *
 * @param sourceChunks    chunk indexes (within the document) that the answer cited
 * @param chunksSearched  number of chunks retrieved and passed to the model
 */
public record QueryResponse(
        String question,
        String answer,
        List<Integer> sourceChunks,
        int chunksSearched
) {
}
