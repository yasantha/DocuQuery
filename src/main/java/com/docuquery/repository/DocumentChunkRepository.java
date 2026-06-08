package com.docuquery.repository;

import com.docuquery.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(Long documentId);

    long countByDocumentId(Long documentId);

    /**
     * Returns the {@code limit} chunks within a document most similar to the query
     * vector, ordered by ascending cosine distance ({@code <=>}, the pgvector
     * cosine operator that the IVFFlat index is built for).
     *
     * <p>The query vector is passed as a pgvector text literal (e.g. {@code "[0.1,0.2,...]"})
     * and cast to {@code vector} in SQL, avoiding any dependency on HQL vector-function
     * registration.</p>
     */
    @Query(value = """
            SELECT * FROM document_chunks
            WHERE document_id = :documentId
            ORDER BY embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentChunk> findNearest(@Param("documentId") Long documentId,
                                    @Param("queryVector") String queryVector,
                                    @Param("limit") int limit);
}
