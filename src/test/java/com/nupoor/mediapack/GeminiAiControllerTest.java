package com.nupoor.mediapack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nupoor.mediapack.controller.GeminiAiController;
import com.nupoor.mediapack.model.CollaborativeLightbox;
import com.nupoor.mediapack.model.MediaAsset;
import com.nupoor.mediapack.service.CollaborativeLightboxService;
import com.nupoor.mediapack.service.GeminiAiService;
import com.nupoor.mediapack.service.MediaAssetService;
import com.nupoor.mediapack.service.SolrSearchService;
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

@WebMvcTest(GeminiAiController.class)
class GeminiAiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GeminiAiService geminiAiService;

    @MockBean
    private MediaAssetService assetService;

    @MockBean
    private SolrSearchService solrSearchService;

    @MockBean
    private CollaborativeLightboxService lightboxService;

    @Test
    @DisplayName("GET /api/ai/status — returns Gemini API configuration status")
    void testGetAiStatus() throws Exception {
        Map<String, Object> statusMap = Map.of(
            "configured", true,
            "model", "gemma-4-31b",
            "provider", "Google Gemini API (Gemma 4 31B)"
        );
        Mockito.when(geminiAiService.getStatus()).thenReturn(statusMap);

        mockMvc.perform(get("/api/ai/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.model").value("gemma-4-31b"));
    }

    @Test
    @DisplayName("POST /api/ai/enrich-asset — returns multi-lingual enriched metadata")
    void testEnrichAsset() throws Exception {
        MediaAsset asset = new MediaAsset("SKU-99", "cap_screw.png", "assets/cap.png", "IMAGE_PNG", 1000, 1920, 1080, List.of("screw"));
        Map<String, Object> enriched = Map.of(
            "tags", List.of("screw", "fastener", "de-catalog"),
            "localizedDescription", "German catalog description for cap screw",
            "altText", "Würth Cap Screw",
            "aiPowered", true
        );

        Mockito.when(geminiAiService.enrichAssetMetadata(any(), eq("DE"))).thenReturn(enriched);

        mockMvc.perform(post("/api/ai/enrich-asset")
                .header("X-Tenant-Region", "DE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(asset)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.altText").value("Würth Cap Screw"))
            .andExpect(jsonPath("$.aiPowered").value(true));
    }

    @Test
    @DisplayName("GET /api/ai/smart-search — translates NL query to Solr and returns results")
    void testSmartSearch() throws Exception {
        Map<String, Object> translation = Map.of(
            "queryTerm", "bolt",
            "fileType", "IMAGE_PNG",
            "tag", "würth",
            "explanation", "Translated high res png bolts query"
        );
        Map<String, Object> searchResults = Map.of("hits", List.of(), "total", 0L);

        Mockito.when(geminiAiService.translateNaturalLanguageToSolr("high res png bolts"))
                .thenReturn(translation);
        Mockito.when(solrSearchService.searchAssets("bolt", "IMAGE_PNG", "würth", 50))
                .thenReturn(searchResults);

        mockMvc.perform(get("/api/ai/smart-search")
                .param("prompt", "high res png bolts")
                .header("X-Tenant-Region", "DE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originalPrompt").value("high res png bolts"))
            .andExpect(jsonPath("$.aiTranslation.fileType").value("IMAGE_PNG"));
    }

    @Test
    @DisplayName("POST /api/ai/generate-lightbox — creates prompt-driven smart lightbox")
    void testGenerateSmartLightbox() throws Exception {
        CollaborativeLightbox lightbox = new CollaborativeLightbox("AI Campaign", "NupoorD");
        Map<String, Object> aiResult = Map.of(
            "title", "AI Campaign Collection",
            "description", "Spring tools bundle",
            "selectedAssetIds", List.of("asset-1")
        );

        Mockito.when(assetService.getAllAssets()).thenReturn(List.of());
        Mockito.when(geminiAiService.generateSmartLightbox(anyString(), anyList(), anyString()))
                .thenReturn(aiResult);
        Mockito.when(lightboxService.createLightbox(anyString(), anyString()))
                .thenReturn(lightbox);
        Mockito.when(lightboxService.getLightboxById(any()))
                .thenReturn(Optional.of(lightbox));

        mockMvc.perform(post("/api/ai/generate-lightbox")
                .param("prompt", "Spring tools campaign")
                .param("ownerUserId", "NupoorD"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aiResult.title").value("AI Campaign Collection"));
    }

    @Test
    @DisplayName("POST /api/ai/generate-manifest — generates package manifest markdown")
    void testGenerateManifest() throws Exception {
        Mockito.when(geminiAiService.generateExportManifest(anyList(), eq("DE")))
                .thenReturn("# Package Manifest\n- SKU-001");

        mockMvc.perform(post("/api/ai/generate-manifest")
                .header("X-Tenant-Region", "DE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("assetKeys", List.of("SKU-001")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifest").value("# Package Manifest\n- SKU-001"));
    }
}
