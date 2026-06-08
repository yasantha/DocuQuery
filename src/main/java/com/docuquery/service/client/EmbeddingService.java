package com.docuquery.service.client;

import com.docuquery.config.OpenAiProperties;
import com.docuquery.config.RagProperties;
import com.docuquery.exception.ApiException;
import com.docuquery.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calls the OpenAI {@code /v1/embeddings} endpoint to embed text (SPEC §6.1).
 *
 * <p>Inputs are sent in batches of at most {@code rag.max-batch-size} (OpenAI's
 * per-call limit) and the resulting vectors are returned in input order.</p>
 */
@Service
public class EmbeddingService {

    private final WebClient webClient;
    private final OpenAiProperties props;
    private final int batchSize;
    private final Duration timeout;

    public EmbeddingService(@Qualifier("openAiWebClient") WebClient openAiWebClient,
                            OpenAiProperties props,
                            RagProperties ragProps) {
        this.webClient = openAiWebClient;
        this.props = props;
        this.batchSize = ragProps.maxBatchSize();
        this.timeout = Duration.ofSeconds(props.timeoutSeconds());
    }

    /**
     * Embeds a single text (convenience for the query pipeline).
     */
    public float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    /**
     * Embeds all texts, splitting into batches of at most {@link #batchSize}.
     * Returned vectors align positionally with the input list.
     *
     * @throws ApiException with {@link ErrorCode#EMBEDDING_API_ERROR} on any API failure
     */
    public List<float[]> embed(List<String> texts) {
        List<float[]> all = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            all.addAll(embedBatch(batch));
        }
        return all;
    }

    private List<float[]> embedBatch(List<String> batch) {
        EmbeddingRequest request = new EmbeddingRequest(
                props.embeddingModel(), batch, props.embeddingDimensions());
        try {
            EmbeddingResponse response = webClient.post()
                    .uri("/v1/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(EmbeddingResponse.class)
                    .block(timeout);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new ApiException(ErrorCode.EMBEDDING_API_ERROR,
                        "OpenAI returned an empty embedding response");
            }
            // OpenAI returns results indexed by input position; order defensively.
            return response.data().stream()
                    .sorted(Comparator.comparingInt(EmbeddingData::index))
                    .map(EmbeddingData::embedding)
                    .toList();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.EMBEDDING_API_ERROR,
                    "OpenAI embedding call failed", e);
        }
    }

    // ---- OpenAI request/response DTOs ----

    record EmbeddingRequest(String model, List<String> input, int dimensions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<EmbeddingData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingData(int index, float[] embedding) {
    }
}
