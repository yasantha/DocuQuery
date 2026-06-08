package com.docuquery.controller;

import com.docuquery.dto.response.QueryResponse;
import com.docuquery.exception.ApiException;
import com.docuquery.exception.ErrorCode;
import com.docuquery.service.query.QueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    QueryService queryService;

    private static String body(String question) {
        return "{\"question\":\"" + question + "\"}";
    }

    @Test
    void queryReturnsAnswerWithSources() throws Exception {
        when(queryService.query(anyLong(), anyString()))
                .thenReturn(new QueryResponse("What is the late fee?",
                        "According to Excerpt 1, 1.5% monthly.", List.of(1), 5));

        mockMvc.perform(post("/api/documents/1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("What is the late fee?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("According to Excerpt 1, 1.5% monthly."))
                .andExpect(jsonPath("$.sourceChunks[0]").value(1))
                .andExpect(jsonPath("$.chunksSearched").value(5));
    }

    @Test
    void blankQuestionReturns400() throws Exception {
        mockMvc.perform(post("/api/documents/1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("QUESTION_BLANK"));
    }

    @Test
    void tooLongQuestionReturns400() throws Exception {
        String longQuestion = "a".repeat(1001);
        mockMvc.perform(post("/api/documents/1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(longQuestion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("QUESTION_TOO_LONG"));
    }

    @Test
    void unknownDocumentReturns404() throws Exception {
        when(queryService.query(anyLong(), anyString()))
                .thenThrow(new ApiException(ErrorCode.DOCUMENT_NOT_FOUND, "nope"));

        mockMvc.perform(post("/api/documents/99/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("anything")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void documentNotReadyReturns409() throws Exception {
        when(queryService.query(anyLong(), anyString()))
                .thenThrow(new ApiException(ErrorCode.DOCUMENT_NOT_READY, "still processing"));

        mockMvc.perform(post("/api/documents/1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("anything")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DOCUMENT_NOT_READY"));
    }

    @Test
    void historyReturnsList() throws Exception {
        when(queryService.history(1L)).thenReturn(List.of());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/documents/1/query/history"))
                .andExpect(status().isOk());
    }
}
