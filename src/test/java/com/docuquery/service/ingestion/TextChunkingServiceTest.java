package com.docuquery.service.ingestion;

import com.docuquery.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkingServiceTest {

    private final TextChunkingService service =
            new TextChunkingService(new RagProperties(500, 50, 5, 100));

    @Test
    void returnsEmptyListForNullOrBlank() {
        assertThat(service.chunk(null)).isEmpty();
        assertThat(service.chunk("")).isEmpty();
        assertThat(service.chunk("   \n\t  ")).isEmpty();
    }

    @Test
    void shortTextProducesSingleChunk() {
        String text = "This is a short document.";
        List<String> chunks = service.chunk(text);
        assertThat(chunks).containsExactly(text);
    }

    @Test
    void longTextSplitsIntoMultipleBoundedChunks() {
        String text = "a".repeat(1200); // no sentence boundaries
        List<String> chunks = service.chunk(text);

        // 0..500, 450..950, 900..1200  → 3 chunks
        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(500));
        assertThat(chunks.get(0)).hasSize(500);
        assertThat(chunks.get(2)).hasSize(300);
    }

    @Test
    void consecutiveChunksOverlap() {
        String text = "a".repeat(1200);
        List<String> chunks = service.chunk(text);

        // With 50-char overlap, the last 50 chars of chunk N equal the
        // first 50 chars of chunk N+1.
        String tailOf0 = chunks.get(0).substring(chunks.get(0).length() - 50);
        String headOf1 = chunks.get(1).substring(0, 50);
        assertThat(tailOf0).isEqualTo(headOf1);
    }

    @Test
    void breaksAtSentenceBoundaryWhenPeriodInSecondHalf() {
        // Period at index 400 (second half of a 500-char window) → break after it.
        String text = "a".repeat(400) + "." + "b".repeat(300);
        List<String> chunks = service.chunk(text);

        assertThat(chunks.get(0)).endsWith(".");
        assertThat(chunks.get(0)).hasSize(401);
    }

    @Test
    void doesNotBreakWhenPeriodOnlyInFirstHalf() {
        // Period at index 100 (first half) → ignored; break at the char limit.
        String text = "a".repeat(100) + "." + "b".repeat(600);
        List<String> chunks = service.chunk(text);

        assertThat(chunks.get(0)).hasSize(500);
        assertThat(chunks.get(0)).doesNotEndWith(".");
    }

    @Test
    void reassemblingChunksCoversEntireText() {
        String text = "a".repeat(1200);
        List<String> chunks = service.chunk(text);
        // Every character index must appear in at least one chunk (no gaps).
        int covered = chunks.get(0).length();
        for (int i = 1; i < chunks.size(); i++) {
            covered += chunks.get(i).length() - 50; // subtract overlap
        }
        assertThat(covered).isEqualTo(1200);
    }
}
