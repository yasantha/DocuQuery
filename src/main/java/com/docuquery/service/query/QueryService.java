package com.docuquery.service.query;

import com.docuquery.config.RagProperties;
import com.docuquery.dto.response.QueryHistoryResponse;
import com.docuquery.dto.response.QueryResponse;
import com.docuquery.entity.Document;
import com.docuquery.entity.DocumentChunk;
import com.docuquery.entity.QueryHistory;
import com.docuquery.exception.ApiException;
import com.docuquery.exception.ErrorCode;
import com.docuquery.repository.DocumentRepository;
import com.docuquery.repository.QueryHistoryRepository;
import com.docuquery.service.client.ClaudeClient;
import com.docuquery.service.client.EmbeddingService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orchestrates the query pipeline (SPEC §6.2): validate → embed → retrieve →
 * build context → generate → persist history.
 */
@Service
public class QueryService {

    private static final Pattern EXCERPT_REF =
            Pattern.compile("Excerpt\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private final DocumentRepository documentRepository;
    private final QueryHistoryRepository queryHistoryRepository;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final ContextBuilderService contextBuilderService;
    private final ClaudeClient claudeClient;
    private final int topK;

    public QueryService(DocumentRepository documentRepository,
                        QueryHistoryRepository queryHistoryRepository,
                        EmbeddingService embeddingService,
                        VectorSearchService vectorSearchService,
                        ContextBuilderService contextBuilderService,
                        ClaudeClient claudeClient,
                        RagProperties ragProps) {
        this.documentRepository = documentRepository;
        this.queryHistoryRepository = queryHistoryRepository;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.contextBuilderService = contextBuilderService;
        this.claudeClient = claudeClient;
        this.topK = ragProps.topKResults();
    }

    public QueryResponse query(Long documentId, String question) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.DOCUMENT_NOT_FOUND,
                        "Document with id " + documentId + " does not exist"));

        if (!"ready".equals(document.getStatus())) {
            throw new ApiException(ErrorCode.DOCUMENT_NOT_READY,
                    "Document " + documentId + " is not ready (status: " + document.getStatus() + ")");
        }

        float[] questionVector = embeddingService.embedOne(question);
        List<DocumentChunk> retrieved = vectorSearchService.search(documentId, questionVector, topK);
        String context = contextBuilderService.build(retrieved);
        String answer = claudeClient.generate(context, question);

        List<Integer> sourceChunks = mapCitedExcerptsToChunkIndexes(answer, retrieved);

        saveHistory(document, question, answer, retrieved.size(), sourceChunks);

        return new QueryResponse(question, answer, sourceChunks, retrieved.size());
    }

    /**
     * Returns the query history for a document, most recent first (SPEC §5).
     */
    public List<QueryHistoryResponse> history(Long documentId) {
        return queryHistoryRepository.findByDocumentIdOrderByQueriedAtDesc(documentId).stream()
                .map(QueryHistoryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Parses {@code "Excerpt N"} references out of the answer and maps each (1-based)
     * excerpt position to the underlying chunk's {@code chunkIndex}. Returns distinct,
     * ascending chunk indexes; references outside the retrieved range are ignored.
     */
    private List<Integer> mapCitedExcerptsToChunkIndexes(String answer, List<DocumentChunk> retrieved) {
        Matcher matcher = EXCERPT_REF.matcher(answer);
        return matcher.results()
                .map(r -> Integer.parseInt(r.group(1)))
                .filter(n -> n >= 1 && n <= retrieved.size())
                .map(n -> retrieved.get(n - 1).getChunkIndex())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private void saveHistory(Document document, String question, String answer,
                             int chunksUsed, List<Integer> sourceChunks) {
        QueryHistory history = new QueryHistory();
        history.setDocument(document);
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setChunksUsed(chunksUsed);
        history.setSourceChunkIndexes(sourceChunks.stream().mapToInt(Integer::intValue).toArray());
        queryHistoryRepository.save(history);
    }
}
