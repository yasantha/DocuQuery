package com.docuquery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code rag.*} configuration block (see application.yml).
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        int chunkSize,
        int chunkOverlap,
        int topKResults,
        int maxBatchSize
) {
}
