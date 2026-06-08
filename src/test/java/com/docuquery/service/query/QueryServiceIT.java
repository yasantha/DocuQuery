package com.docuquery.service.query;

import com.docuquery.dto.response.QueryResponse;
import com.docuquery.entity.Document;
import com.docuquery.entity.DocumentChunk;
import com.docuquery.exception.ApiException;
import com.docuquery.exception.ErrorCode;
import com.docuquery.repository.DocumentChunkRepository;
import com.docuquery.repository.DocumentRepository;
import com.docuquery.repository.QueryHistoryRepository;
import com.docuquery.service.client.ClaudeClient;
import com.docuquery.service.client.EmbeddingService;
import com.docuquery.support.PgVectorContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Full query pipeline against a real pgvector DB, with OpenAI embedding and Claude
 * generation mocked (SPEC §10).
 */
@SpringBootTest
class QueryServiceIT extends PgVectorContainerSupport {

    @Autowired
    QueryService queryService;
    @Autowired
    DocumentRepository documentRepository;
    @Autowired
    DocumentChunkRepository chunkRepository;
    @Autowired
    QueryHistoryRepository queryHistoryRepository;

    @MockBean
    EmbeddingService embeddingService;
    @MockBean
    ClaudeClient claudeClient;

    @Test
    void answersQuestionAndMapsCitedExcerptToChunkIndex() {
        Document doc = readyDocumentWithChunks();
        // Question embedding identical to chunk index 1's vector → it ranks first (Excerpt 1).
        when(embeddingService.embedOne(anyString())).thenReturn(axisVector(1));
        when(claudeClient.generate(anyString(), anyString()))
                .thenReturn("According to Excerpt 1, the late fee is 1.5% monthly.");

        QueryResponse response = queryService.query(doc.getId(), "What is the late fee?");

        assertThat(response.question()).isEqualTo("What is the late fee?");
        assertThat(response.chunksSearched()).isGreaterThanOrEqualTo(1);
        // Excerpt 1 maps to the top-ranked chunk, whose chunkIndex is 1.
        assertThat(response.sourceChunks()).containsExactly(1);

        assertThat(queryHistoryRepository.findByDocumentIdOrderByQueriedAtDesc(doc.getId()))
                .hasSize(1)
                .first()
                .satisfies(h -> assertThat(h.getChunksUsed()).isEqualTo(response.chunksSearched()));
    }

    @Test
    void rejectsUnknownDocument() {
        assertThatThrownBy(() -> queryService.query(99999L, "anything"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    void rejectsDocumentThatIsNotReady() {
        Document processing = new Document();
        processing.setFilename("wip.pdf");
        processing.setStatus("processing");
        processing = documentRepository.save(processing);

        Long id = processing.getId();
        assertThatThrownBy(() -> queryService.query(id, "anything"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_READY);
    }

    private Document readyDocumentWithChunks() {
        Document doc = new Document();
        doc.setFilename("contract.pdf");
        doc.setStatus("ready");
        doc.setTotalChunks(3);
        doc = documentRepository.save(doc);
        for (int i = 0; i < 3; i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(doc);
            chunk.setChunkIndex(i);
            chunk.setContent("chunk " + i);
            chunk.setCharCount(7);
            chunk.setEmbedding(axisVector(i));
            chunkRepository.save(chunk);
        }
        return doc;
    }

    private static float[] axisVector(int axis) {
        float[] v = new float[1536];
        v[axis] = 1.0f;
        return v;
    }
}
