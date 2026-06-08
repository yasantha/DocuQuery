package com.docuquery.repository;

import com.docuquery.entity.Document;
import com.docuquery.entity.DocumentChunk;
import com.docuquery.entity.QueryHistory;
import com.docuquery.support.PgVectorContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the schema (Flyway V1), entity mappings — including the
 * {@code vector(1536)} embedding column — and repository queries against a
 * real pgvector database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcTemplateAutoConfiguration.class)
class RepositoryIT extends PgVectorContainerSupport {

    @Autowired
    DocumentRepository documentRepository;
    @Autowired
    DocumentChunkRepository chunkRepository;
    @Autowired
    QueryHistoryRepository queryHistoryRepository;

    @Test
    void persistsAndReloadsDocumentWithVectorEmbedding() {
        Document doc = new Document();
        doc.setFilename("contract.pdf");
        doc.setStatus("ready");
        doc.setTotalChunks(1);
        doc = documentRepository.save(doc);

        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(doc);
        chunk.setChunkIndex(0);
        chunk.setContent("Payment is due within 30 days.");
        chunk.setCharCount(30);
        chunk.setEmbedding(deterministicVector());
        chunkRepository.save(chunk);

        List<DocumentChunk> reloaded = chunkRepository.findByDocumentIdOrderByChunkIndex(doc.getId());
        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.get(0).getEmbedding()).hasSize(1536);
        assertThat(reloaded.get(0).getContent()).contains("30 days");
        assertThat(chunkRepository.countByDocumentId(doc.getId())).isEqualTo(1);
    }

    @Test
    void cascadeDeletesChunksAndHistoryWithDocument() {
        Document doc = new Document();
        doc.setFilename("report.pdf");
        doc = documentRepository.save(doc);

        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(doc);
        chunk.setChunkIndex(0);
        chunk.setContent("Q3 revenue was $4.2M.");
        chunk.setCharCount(21);
        chunk.setEmbedding(deterministicVector());
        chunkRepository.save(chunk);

        QueryHistory history = new QueryHistory();
        history.setDocument(doc);
        history.setQuestion("What was Q3 revenue?");
        history.setAnswer("According to Excerpt 1, Q3 revenue was $4.2M.");
        history.setChunksUsed(1);
        history.setSourceChunkIndexes(new int[]{0});
        queryHistoryRepository.save(history);

        Long docId = doc.getId();
        documentRepository.deleteById(docId);
        documentRepository.flush();

        assertThat(chunkRepository.countByDocumentId(docId)).isZero();
        assertThat(queryHistoryRepository.findByDocumentIdOrderByQueriedAtDesc(docId)).isEmpty();
    }

    private static float[] deterministicVector() {
        float[] v = new float[1536];
        for (int i = 0; i < v.length; i++) {
            v[i] = (i % 10) / 10.0f;
        }
        return v;
    }
}
