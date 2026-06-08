package com.docuquery.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI metadata shown at {@code /swagger-ui.html} (SPEC §2 goals).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI docuQueryOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("DocuQuery API")
                .version("1.0.0")
                .description("RAG backend for natural-language querying over uploaded PDF documents."));
    }
}
