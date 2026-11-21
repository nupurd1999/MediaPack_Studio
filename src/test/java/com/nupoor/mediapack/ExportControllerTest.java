package com.nupoor.mediapack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nupoor.mediapack.controller.ExportController;
import com.nupoor.mediapack.model.ExportJobRequest;
import com.nupoor.mediapack.model.ExportJobState;
import com.nupoor.mediapack.service.AsyncMediaPackagerWorker;
import com.nupoor.mediapack.service.RedisTaskPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExportController.class)
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AsyncMediaPackagerWorker exportWorker;

    @MockBean
    private RedisTaskPublisher redisTaskPublisher;

    @MockBean
    private com.nupoor.mediapack.service.GeminiAiService geminiAiService;

    @Test
    @DisplayName("POST /api/exports/trigger — returns 202 Accepted with jobId and progressTopic")
    void testTriggerExport_ReturnsAccepted() throws Exception {
        ExportJobRequest request = new ExportJobRequest("job-test-01", null, List.of("SKU-001", "SKU-002"), "DE");

        ExportJobState mockState = new ExportJobState("job-test-01", "DE", List.of("SKU-001", "SKU-002"));
        Mockito.when(redisTaskPublisher.enqueue(any(), eq("job-test-01"), anyString())).thenReturn(mockState);

        mockMvc.perform(post("/api/exports/trigger")
                .header("X-Tenant-Region", "DE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobId").value("job-test-01"))
            .andExpect(jsonPath("$.progressTopic").value("/topic/export-progress/job-test-01"))
            .andExpect(jsonPath("$.statusEndpoint").value("/api/exports/job-test-01"));
    }

    @Test
    @DisplayName("GET /api/exports/{jobId} — returns persisted Redis job state")
    void testGetJobStatus_ReturnsRedisState() throws Exception {
        Map<Object, Object> redisState = Map.of(
            "jobId",  "job-test-01",
            "status", "PROCESSING",
            "progress", "60"
        );
        Mockito.when(redisTaskPublisher.getJobState("job-test-01")).thenReturn(Optional.of(redisState));

        mockMvc.perform(get("/api/exports/job-test-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSING"))
            .andExpect(jsonPath("$.progress").value("60"));
    }

    @Test
    @DisplayName("GET /api/exports/{jobId} — returns 404 for unknown job")
    void testGetJobStatus_Returns404ForUnknownJob() throws Exception {
        Mockito.when(redisTaskPublisher.getJobState("unknown-job")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/exports/unknown-job"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/exports/download/{jobId} — returns ZIP package attachment")
    void testDownloadExportPackage_ReturnsZip() throws Exception {
        Mockito.when(geminiAiService.generateExportManifest(anyList(), anyString()))
                .thenReturn("# Mock Package Manifest");

        mockMvc.perform(get("/api/exports/download/job-test-01")
                .header("X-Tenant-Region", "DE"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"mediapack-export-job-test-01.zip\""))
            .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }
}
