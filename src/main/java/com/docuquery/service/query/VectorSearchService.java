package com.docuquery.service.query;

import com.docuquery.entity.DocumentChunk;
import com.docuquery.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;

/**
 * Cosine-similarity retrieval over a single document's chunks (SPEC §6.2).
 */
@Service
public class VectorSearchService {

    private final DocumentChunkRepository chunkRepository;

    public VectorSearchService(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    /**
     * Retrieves the top-{@code k} chunks of a document most similar to the query vector.
     */
    public List<DocumentChunk> search(Long documentId, float[] queryVector, int k) {
        return chunkRepository.findNearest(documentId, toVectorLiteral(queryVector), k);
    }

    /** Formats a vector as the pgvector text literal {@code [v1,v2,...]}. */
    static String toVectorLiteral(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : vector) {
            joiner.add(Float.toString(v));
        }
        return joiner.toString();
    }
}
