package com.docuquery.service.ingestion;

import com.docuquery.dto.response.DocumentResponse;
import com.docuquery.entity.Document;
import com.docuquery.entity.DocumentChunk;
import com.docuquery.exception.ApiException;
import com.docuquery.exception.ErrorCode;
import com.docuquery.repository.DocumentChunkRepository;
import com.docuquery.repository.DocumentRepository;
import com.docuquery.service.client.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the ingestion pipeline (SPEC §6.1): validate → persist →
 * extract → chunk → embed → store → mark ready.
 */
@Slf4j
@Service
public class DocumentIngestionService {

    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024; // 50MB

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final PdfExtractionService pdfExtractionService;
    private final TextChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    public DocumentIngestionService(DocumentRepository documentRepository,
                                    DocumentChunkRepository chunkRepository,
                                    PdfExtractionService pdfExtractionService,
                                    TextChunkingService chunkingService,
                                    EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.pdfExtractionService = pdfExtractionService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
    }

    public DocumentResponse ingest(MultipartFile file) {
        byte[] bytes = validate(file);

        Document document = new Document();
        document.setFilename(file.getOriginalFilename());
        document.setFileSizeKb((int) (file.getSize() / 1024));
        document.setStatus("processing");
        document = documentRepository.save(document);

        try {
            String text = pdfExtractionService.extractText(bytes);
            List<String> chunkTexts = chunkingService.chunk(text);

            if (!chunkTexts.isEmpty()) {
                List<float[]> embeddings = embeddingService.embed(chunkTexts);
                List<DocumentChunk> chunks = buildChunks(document, chunkTexts, embeddings);
                chunkRepository.saveAll(chunks);
            }

            document.setTotalChunks(chunkTexts.size());
            document.setStatus("ready");
            document = documentRepository.save(document);
            return DocumentResponse.from(document);

        } catch (RuntimeException e) {
            document.setStatus("failed");
            documentRepository.save(document);
            log.error("Ingestion failed for document id={} ({}): {}",
                    document.getId(), document.getFilename(), e.getMessage(), e);
            throw e;
        }
    }

    private List<DocumentChunk> buildChunks(Document document,
                                            List<String> chunkTexts,
                                            List<float[]> embeddings) {
        List<DocumentChunk> chunks = new ArrayList<>(chunkTexts.size());
        for (int i = 0; i < chunkTexts.size(); i++) {
            String content = chunkTexts.get(i);
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(i);
            chunk.setContent(content);
            chunk.setCharCount(content.length());
            chunk.setEmbedding(embeddings.get(i));
            chunks.add(chunk);
        }
        return chunks;
    }

    /**
     * Validates the upload and returns its bytes (SPEC §5 / §8).
     */
    private byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.EMPTY_FILE, "Uploaded file is empty");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            throw new ApiException(ErrorCode.INVALID_FILE_TYPE, "Uploaded file is not a PDF");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE, "File exceeds the 50MB limit");
        }
        try {
            return file.getBytes();
        } catch (java.io.IOException e) {
            throw new ApiException(ErrorCode.PDF_EXTRACTION_FAILED,
                    "Could not read uploaded file", e);
        }
    }
}
