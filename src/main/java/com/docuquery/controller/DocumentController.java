package com.docuquery.controller;

import com.docuquery.dto.response.DocumentResponse;
import com.docuquery.entity.Document;
import com.docuquery.exception.ApiException;
import com.docuquery.exception.ErrorCode;
import com.docuquery.repository.DocumentRepository;
import com.docuquery.service.ingestion.DocumentIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Document upload and management endpoints (SPEC §5).
 */
@Tag(name = "Documents", description = "Upload, list, retrieve, and delete PDF documents")
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final DocumentRepository documentRepository;

    public DocumentController(DocumentIngestionService ingestionService,
                              DocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
    }

    @Operation(summary = "Upload and ingest a PDF document")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse upload(@RequestParam("file") MultipartFile file) {
        return ingestionService.ingest(file);
    }

    @Operation(summary = "List all uploaded documents")
    @GetMapping
    public List<DocumentResponse> list() {
        return documentRepository.findAll().stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Operation(summary = "Get a single document by ID")
    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id) {
        return documentRepository.findById(id)
                .map(DocumentResponse::from)
                .orElseThrow(() -> notFound(id));
    }

    @Operation(summary = "Delete a document and all its chunks")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Document document = documentRepository.findById(id).orElseThrow(() -> notFound(id));
        documentRepository.delete(document);
        return ResponseEntity.noContent().build();
    }

    private static ApiException notFound(Long id) {
        return new ApiException(ErrorCode.DOCUMENT_NOT_FOUND,
                "Document with id " + id + " does not exist");
    }
}
