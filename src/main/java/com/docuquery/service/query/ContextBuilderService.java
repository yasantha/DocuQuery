package com.docuquery.service.query;

import com.docuquery.entity.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Builds the excerpt context string passed to Claude (SPEC §6.2).
 *
 * <p>Retrieved chunks are numbered 1..N as "Excerpt" blocks. Claude references
 * these excerpt numbers in its answer; {@code QueryService} maps the cited numbers
 * back to the underlying document chunk indexes.</p>
 */
@Service
public class ContextBuilderService {

    public String build(List<DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append("--- Excerpt ").append(i + 1).append(" ---\n");
            sb.append(chunks.get(i).getContent());
        }
        return sb.toString();
    }
}
