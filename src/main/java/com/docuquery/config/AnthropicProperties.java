package com.docuquery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code anthropic.*} configuration block (see application.yml).
 */
@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
        String apiKey,
        String model,
        String baseUrl,
        int maxTokens,
        int timeoutSeconds
) {
}
