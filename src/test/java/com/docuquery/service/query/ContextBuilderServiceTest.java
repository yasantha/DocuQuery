package com.docuquery.service.query;

import com.docuquery.entity.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBuilderServiceTest {

    private final ContextBuilderService service = new ContextBuilderService();

    @Test
    void emptyChunksProduceEmptyContext() {
        assertThat(service.build(List.of())).isEmpty();
    }

    @Test
    void singleChunkIsLabelledExcerptOne() {
        String context = service.build(List.of(chunk("Payment is due in 30 days.")));
        assertThat(context).isEqualTo("--- Excerpt 1 ---\nPayment is due in 30 days.");
    }

    @Test
    void multipleChunksAreNumberedAndBlankLineSeparated() {
        String context = service.build(List.of(chunk("First."), chunk("Second."), chunk("Third.")));
        assertThat(context).isEqualTo("""
                --- Excerpt 1 ---
                First.

                --- Excerpt 2 ---
                Second.

                --- Excerpt 3 ---
                Third.""");
    }

    private static DocumentChunk chunk(String content) {
        DocumentChunk c = new DocumentChunk();
        c.setContent(content);
        return c;
    }
}
