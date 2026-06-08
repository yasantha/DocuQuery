package com.docuquery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Centralises construction of the outbound {@link WebClient}s, so base URLs,
 * auth headers, and the Anthropic version header live in one place rather than
 * being assembled inside each service.
 */
@Configuration
public class WebClientConfig {

    /** Anthropic API version pin (see SPEC §6.2 / Messages API). */
    public static final String ANTHROPIC_VERSION = "2023-06-01";

    @Bean
    public WebClient openAiWebClient(WebClient.Builder builder, OpenAiProperties props) {
        return builder
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .build();
    }

    @Bean
    public WebClient anthropicWebClient(WebClient.Builder builder, AnthropicProperties props) {
        return builder
                .baseUrl(props.baseUrl())
                .defaultHeader("x-api-key", props.apiKey())
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
    }
}
