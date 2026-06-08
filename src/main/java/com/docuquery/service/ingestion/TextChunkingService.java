package com.docuquery.service.ingestion;

import com.docuquery.config.RagProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits cleaned document text into overlapping chunks (SPEC §6.1).
 *
 * <p>Strategy: target {@code chunkSize} characters per chunk with {@code chunkOverlap}
 * characters carried over between consecutive chunks. When a sentence-ending period
 * falls in the second half of the tentative chunk, the break is moved to just after
 * that period so chunks tend to end on sentence boundaries.</p>
 */
@Service
public class TextChunkingService {

    private final int chunkSize;
    private final int overlap;

    public TextChunkingService(RagProperties props) {
        this.chunkSize = props.chunkSize();
        this.overlap = props.chunkOverlap();
    }

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null) {
            return chunks;
        }
        String t = text.strip();
        if (t.isEmpty()) {
            return chunks;
        }

        int length = t.length();
        int start = 0;

        while (start < length) {
            int tentativeEnd = Math.min(start + chunkSize, length);
            int end = tentativeEnd;

            // Only attempt a sentence-boundary break if this is not the final chunk.
            if (tentativeEnd < length) {
                int lastPeriod = t.lastIndexOf('.', tentativeEnd - 1);
                int secondHalfStart = start + chunkSize / 2;
                if (lastPeriod >= secondHalfStart) {
                    end = lastPeriod + 1; // include the period
                }
            }

            String chunk = t.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= length) {
                break;
            }

            // Advance with overlap; guaranteed to make progress because
            // (chunkSize / 2) > overlap for the configured defaults.
            int next = end - overlap;
            start = Math.max(next, start + 1);
        }

        return chunks;
    }
}
