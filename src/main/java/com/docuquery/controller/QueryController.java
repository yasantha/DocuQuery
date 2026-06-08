package com.docuquery.controller;

import com.docuquery.dto.request.QueryRequest;
import com.docuquery.dto.response.QueryHistoryResponse;
import com.docuquery.dto.response.QueryResponse;
import com.docuquery.service.query.QueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Natural-language query and history endpoints for a document (SPEC §5).
 */
@Tag(name = "Query", description = "Ask questions about a document and read past answers")
@RestController
@RequestMapping("/api/documents/{documentId}/query")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "Ask a natural-language question about a document")
    @PostMapping
    public QueryResponse query(@PathVariable Long documentId,
                               @Valid @RequestBody QueryRequest request) {
        return queryService.query(documentId, request.question());
    }

    @Operation(summary = "Retrieve query history for a document")
    @GetMapping("/history")
    public List<QueryHistoryResponse> history(@PathVariable Long documentId) {
        return queryService.history(documentId);
    }
}
