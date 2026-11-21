package com.nupoor.mediapack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nupoor.mediapack.controller.MediaAssetController;
import com.nupoor.mediapack.model.MediaAsset;
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

@WebMvcTest(MediaAssetController.class)
class MediaAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MediaAssetService assetService;

    @MockBean
    private SolrSearchService solrSearchService;

    @Test
    @DisplayName("POST /api/assets — creates asset and returns 200 with tenant region")
    void testCreateAsset_Returns200() throws Exception {
        MediaAsset asset = new MediaAsset("SKU-WURTH-001", "bolt_highres.png", "assets/bolt.png",
                "IMAGE_PNG", 1_048_576, 1920, 1080, List.of("bolt", "fastener"));

        Mockito.when(assetService.saveAsset(any())).thenReturn(asset);

        mockMvc.perform(post("/api/assets")
                .header("X-Tenant-Region", "DE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(asset)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sku").value("SKU-WURTH-001"))
            .andExpect(jsonPath("$.fileType").value("IMAGE_PNG"));
    }

    @Test
    @DisplayName("GET /api/assets — returns asset list with tenantRegion in response")
    void testGetAllAssets_IncludesTenantRegion() throws Exception {
        MediaAsset asset = new MediaAsset("SKU-NL-001", "catalog_photo.jpg", "assets/nl/catalog.jpg",
                "IMAGE_JPG", 512_000, 800, 600, List.of("catalog", "product"));

        Mockito.when(assetService.getAllAssets()).thenReturn(List.of(asset));

        mockMvc.perform(get("/api/assets")
                .header("X-Tenant-Region", "NL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.assets[0].sku").value("SKU-NL-001"));
    }

    @Test
    @DisplayName("GET /api/assets/{id} — returns 404 for unknown asset ID")
    void testGetAssetById_Returns404() throws Exception {
        Mockito.when(assetService.getAssetById("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/assets/non-existent"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/assets/search — returns Solr faceted search response")
    void testSearchAssets_ReturnsFacetedResponse() throws Exception {
        Map<String, Object> solrResult = Map.of(
            "hits", List.of(Map.of("id", "asset-1", "sku_s", "SKU-001")),
            "total", 1L,
            "facets", Map.of("file_type_s", List.of(Map.of("value", "IMAGE_PNG", "count", 1)))
        );

        Mockito.when(solrSearchService.searchAssets(eq("bolt"), isNull(), isNull(), eq(50)))
               .thenReturn(solrResult);

        mockMvc.perform(get("/api/assets/search")
                .param("q", "bolt")
                .header("X-Tenant-Region", "DE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.facets").exists());
    }

    @Test
    @DisplayName("POST /api/assets/upload — uploads multipart file and returns saved asset")
    void testUploadAsset_ReturnsCreatedAsset() throws Exception {
        org.springframework.mock.web.MockMultipartFile mockFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "screw_drawing.png", "image/png", "sample-image-bytes".getBytes());

        MediaAsset uploaded = new MediaAsset("SKU-SCREW-99", "screw_drawing.png", "assets/de/123/screw_drawing.png",
                "IMAGE_PNG", 18, 0, 0, List.of("screw"));
        uploaded.setId("asset-upload-123");

        Mockito.when(assetService.uploadAsset(any(), any(), any(), any(), eq("DE"))).thenReturn(uploaded);

        mockMvc.perform(multipart("/api/assets/upload")
                .file(mockFile)
                .param("sku", "SKU-SCREW-99")
                .header("X-Tenant-Region", "DE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("asset-upload-123"))
            .andExpect(jsonPath("$.sku").value("SKU-SCREW-99"))
            .andExpect(jsonPath("$.fileName").value("screw_drawing.png"));
    }

    @Test
    @DisplayName("GET /api/assets/{id}/download — downloads asset binary stream")
    void testDownloadAsset_ReturnsBinaryStream() throws Exception {
        MediaAsset asset = new MediaAsset("SKU-1", "doc.pdf", "assets/doc.pdf", "DOCUMENT_PDF", 10, 0, 0, List.of());
        asset.setId("asset-dl-1");
        Mockito.when(assetService.getAssetById("asset-dl-1")).thenReturn(Optional.of(asset));
        Mockito.when(assetService.getAssetInputStream("asset-dl-1"))
                .thenReturn(new java.io.ByteArrayInputStream("pdf-content".getBytes()));

        mockMvc.perform(get("/api/assets/asset-dl-1/download"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"doc.pdf\""))
            .andExpect(content().bytes("pdf-content".getBytes()));
    }
}
