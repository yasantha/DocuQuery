package com.docuquery.controller;

import com.docuquery.dto.response.DocumentResponse;
import com.docuquery.entity.Document;
import com.docuquery.exception.ApiException;
import com.docuquery.exception.ErrorCode;
import com.docuquery.repository.DocumentRepository;
import com.docuquery.service.ingestion.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    DocumentIngestionService ingestionService;
    @MockBean
    DocumentRepository documentRepository;

    private static final DocumentResponse READY =
            new DocumentResponse(1L, "contract.pdf", 42, "ready", LocalDateTime.now());

    @Test
    void uploadReturnsReadyDocument() throws Exception {
        when(ingestionService.ingest(any())).thenReturn(READY);
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "%PDF-1.4".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.totalChunks").value(42));
    }

    @Test
    void uploadOfNonPdfReturns400() throws Exception {
        when(ingestionService.ingest(any()))
                .thenThrow(new ApiException(ErrorCode.INVALID_FILE_TYPE, "Uploaded file is not a PDF"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hi".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_FILE_TYPE"));
    }

    @Test
    void missingFilePartReturns400EmptyFile() throws Exception {
        mockMvc.perform(multipart("/api/documents"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("EMPTY_FILE"));
    }

    @Test
    void listReturnsAllDocuments() throws Exception {
        Document doc = new Document();
        doc.setFilename("contract.pdf");
        doc.setStatus("ready");
        when(documentRepository.findAll()).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("contract.pdf"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/documents/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void deleteReturns204WhenPresent() throws Exception {
        Document doc = new Document();
        doc.setFilename("contract.pdf");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));

        mockMvc.perform(delete("/api/documents/1"))
                .andExpect(status().isNoContent());
        verify(documentRepository).delete(doc);
    }

    @Test
    void deleteReturns404WhenMissing() throws Exception {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/documents/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DOCUMENT_NOT_FOUND"));
    }
}
