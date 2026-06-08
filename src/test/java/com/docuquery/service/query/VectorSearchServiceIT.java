package com.docuquery.service.query;

import com.docuquery.entity.Document;
import com.docuquery.entity.DocumentChunk;
import com.docuquery.repository.DocumentChunkRepository;
import com.docuquery.repository.DocumentRepository;
import com.docuquery.support.PgVectorContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies pgvector cosine similarity search returns the nearest chunks in order,
 * scoped to a single document (SPEC §6.2).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VectorSearchServiceIT extends PgVectorContainerSupport {

    @Autowired
    DocumentRepository documentRepository;
    @Autowired
    DocumentChunkRepository chunkRepository;

    private VectorSearchService service;
    private Document document;

    @BeforeEach
    void setUp() {
        service = new VectorSearchService(chunkRepository);
        document = new Document();
        document.setFilename("doc.pdf");
        document.setStatus("ready");
        document = documentRepository.save(document);
    }

    @Test
    void returnsChunksOrderedByCosineSimilarity() {
        // Three chunks pointing in distinct directions in the first 3 dims.
        saveChunk(0, "alpha", axisVector(0));
        saveChunk(1, "beta", axisVector(1));
        saveChunk(2, "gamma", axisVector(2));

        // Query identical to chunk 1's vector → chunk 1 must rank first.
        List<DocumentChunk> result = service.search(document.getId(), axisVector(1), 3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getChunkIndex()).isEqualTo(1);
        assertThat(result.get(0).getContent()).isEqualTo("beta");
    }

    @Test
    void respectsTopKLimitAndDocumentScope() {
        saveChunk(0, "a", axisVector(0));
        saveChunk(1, "b", axisVector(1));
        saveChunk(2, "c", axisVector(2));

        // A different document whose chunk should never be returned.
        Document other = new Document();
        other.setFilename("other.pdf");
        other = documentRepository.save(other);
        DocumentChunk foreign = new DocumentChunk();
        foreign.setDocument(other);
        foreign.setChunkIndex(0);
        foreign.setContent("foreign");
        foreign.setEmbedding(axisVector(0));
        chunkRepository.save(foreign);

        List<DocumentChunk> result = service.search(document.getId(), axisVector(0), 2);

        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(c -> "foreign".equals(c.getContent()));
    }

    private void saveChunk(int index, String content, float[] embedding) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        chunk.setEmbedding(embedding);
        chunkRepository.save(chunk);
    }

    /** Unit vector along a single axis of a 1536-dim space. */
    private static float[] axisVector(int axis) {
        float[] v = new float[1536];
        v[axis] = 1.0f;
        return v;
    }
}
