package com.docuquery.service.ingestion;

import com.docuquery.dto.response.DocumentResponse;
import com.docuquery.entity.DocumentChunk;
import com.docuquery.repository.DocumentChunkRepository;
import com.docuquery.repository.DocumentRepository;
import com.docuquery.service.client.EmbeddingService;
import com.docuquery.support.PgVectorContainerSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Full ingestion pipeline against a real pgvector DB, with the OpenAI
 * embedding call mocked (SPEC §10).
 */
@SpringBootTest
class DocumentIngestionServiceIT extends PgVectorContainerSupport {

    @Autowired
    DocumentIngestionService ingestionService;
    @Autowired
    DocumentRepository documentRepository;
    @Autowired
    DocumentChunkRepository chunkRepository;

    @MockBean
    EmbeddingService embeddingService;

    @Test
    void ingestsPdfAndPersistsReadyDocumentWithChunks() throws Exception {
        stubDeterministicEmbeddings();
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf",
                pdfBytes("Payment is due within 30 days of the invoice date."));

        DocumentResponse response = ingestionService.ingest(file);

        assertThat(response.status()).isEqualTo("ready");
        assertThat(response.filename()).isEqualTo("contract.pdf");
        assertThat(response.totalChunks()).isGreaterThanOrEqualTo(1);

        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(response.id());
        assertThat(chunks).hasSize(response.totalChunks());
        assertThat(chunks.get(0).getEmbedding()).hasSize(1536);
        assertThat(chunks.get(0).getContent()).contains("30 days");
    }

    @Test
    void rejectsNonPdfFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> ingestionService.ingest(file))
                .isInstanceOf(com.docuquery.exception.ApiException.class)
                .hasMessageContaining("not a PDF");
    }

    private void stubDeterministicEmbeddings() {
        when(embeddingService.embed(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(t -> vector()).toList();
        });
    }

    private static float[] vector() {
        float[] v = new float[1536];
        for (int i = 0; i < v.length; i++) {
            v[i] = (i % 7) / 7.0f;
        }
        return v;
    }

    private static byte[] pdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }
}
