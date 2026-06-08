package com.docuquery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code openai.*} configuration block (see application.yml).
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String embeddingModel,
        int embeddingDimensions,
        String baseUrl,
        int timeoutSeconds
) {
}
