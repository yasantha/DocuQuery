package com.docuquery.service.client;

import com.docuquery.config.AnthropicProperties;
import com.docuquery.exception.ApiException;
import com.docuquery.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Calls the Anthropic Messages API ({@code POST /v1/messages}) to generate a
 * grounded answer from document excerpts (SPEC §6.2).
 */
@Service
public class ClaudeClient {

    /** System prompt verbatim from SPEC §6.2. */
    static final String SYSTEM_PROMPT = """
            You are a precise document analyst.
            Answer questions based ONLY on the provided document excerpts.
            If the answer cannot be found in the excerpts, respond with:
            "The provided document excerpts do not contain information about [topic]."
            Always reference which Excerpt number your answer comes from.
            Be concise, factual, and do not add information beyond what the excerpts contain.""";

    private final WebClient webClient;
    private final AnthropicProperties props;
    private final Duration timeout;

    public ClaudeClient(@Qualifier("anthropicWebClient") WebClient anthropicWebClient,
                        AnthropicProperties props) {
        this.webClient = anthropicWebClient;
        this.props = props;
        this.timeout = Duration.ofSeconds(props.timeoutSeconds());
    }

    /**
     * Generates an answer grounded in {@code context} for {@code question}.
     *
     * @throws ApiException with {@link ErrorCode#GENERATION_API_ERROR} on any API failure
     */
    public String generate(String context, String question) {
        String userContent = "Document excerpts:\n" + context + "\n\nQuestion: " + question;
        ClaudeRequest request = new ClaudeRequest(
                props.model(),
                props.maxTokens(),
                SYSTEM_PROMPT,
                List.of(new Message("user", userContent)));
        try {
            ClaudeResponse response = webClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ClaudeResponse.class)
                    .block(timeout);

            if (response == null || response.content() == null || response.content().isEmpty()) {
                throw new ApiException(ErrorCode.GENERATION_API_ERROR,
                        "Claude returned an empty response");
            }
            String text = response.content().get(0).text();
            if (text == null || text.isBlank()) {
                throw new ApiException(ErrorCode.GENERATION_API_ERROR,
                        "Claude returned no text content");
            }
            return text;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.GENERATION_API_ERROR,
                    "Claude generation call failed", e);
        }
    }

    // ---- Anthropic request/response DTOs ----

    record ClaudeRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<Message> messages) {
    }

    record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClaudeResponse(List<ContentBlock> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {
    }
}
