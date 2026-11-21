package com.nupoor.mediapack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nupoor.mediapack.model.MediaAsset;
import com.nupoor.mediapack.service.GeminiAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GeminiAiService Unit Tests")
class GeminiAiServiceTest {

    private GeminiAiService geminiAiService;

    @BeforeEach
    void setUp() {
        geminiAiService = new GeminiAiService(new ObjectMapper());
    }

    @Test
    @DisplayName("generateExportManifest generates structured Markdown table with file details in fallback mode")
    void testGenerateExportManifest_TableFormat() {
        List<String> assetKeys = List.of("SKU-MP-8821", "SKU-MP-8822", "SKU-MP-8823");
        String manifest = geminiAiService.generateExportManifest(assetKeys, "DE");

        assertNotNull(manifest);
        assertTrue(manifest.contains("# 📦 MediaPack Studio — Package Manifest"));
        assertTrue(manifest.contains("**Tenant Region:** `DE`"));
        assertTrue(manifest.contains("| File Name | SKU Key | Format | File Size |"));
        assertTrue(manifest.contains("Würth M8x30 Stainless Steel Hex Head Bolt DIN 933"));
        assertTrue(manifest.contains("`SKU-MP-8821`"));
        assertTrue(manifest.contains("`IMAGE_PNG`"));
        assertTrue(manifest.contains("12.4 MB"));
    }

    @Test
    @DisplayName("generateExportManifest handles null region safely without NullPointerException")
    void testGenerateExportManifest_NullRegion() {
        List<String> assetKeys = List.of("SKU-MP-8821");
        assertDoesNotThrow(() -> {
            String manifest = geminiAiService.generateExportManifest(assetKeys, null);
            assertNotNull(manifest);
            assertTrue(manifest.contains("**Tenant Region:** `DE`"));
        });
    }

    @Test
    @DisplayName("generateExportManifest handles empty or null asset keys safely")
    void testGenerateExportManifest_EmptyAssetKeys() {
        String manifestNull = geminiAiService.generateExportManifest(null, "US");
        assertNotNull(manifestNull);
        assertTrue(manifestNull.contains("*No files included.*"));
        assertTrue(manifestNull.contains("**Tenant Region:** `US`"));

        String manifestEmpty = geminiAiService.generateExportManifest(List.of(), "NL");
        assertNotNull(manifestEmpty);
        assertTrue(manifestEmpty.contains("*No files included.*"));
        assertTrue(manifestEmpty.contains("**Tenant Region:** `NL`"));
    }

    @Test
    @DisplayName("translateNaturalLanguageToSolr returns fallback query structure when prompt provided")
    void testTranslateNaturalLanguageToSolr() {
        Map<String, Object> result = geminiAiService.translateNaturalLanguageToSolr("find all fastener images");
        assertNotNull(result);
        assertEquals("IMAGE_PNG", result.get("fileType"));
        assertEquals("fastener", result.get("tag"));
        assertTrue((Boolean) result.get("fallbackMode"));
    }

    @Test
    @DisplayName("enrichAssetMetadata provides fallback enrichment when API key is missing")
    void testEnrichAssetMetadata() {
        MediaAsset asset = new MediaAsset("SKU-MP-8821", "bolt.png", "storage/bolt.png", "IMAGE_PNG", 1024, 1920, 1080, List.of("hardware"));
        Map<String, Object> enrichment = geminiAiService.enrichAssetMetadata(asset, "DE");

        assertNotNull(enrichment);
        assertTrue(enrichment.containsKey("tags"));
        assertTrue(enrichment.containsKey("localizedDescription"));
        assertTrue(enrichment.containsKey("suggestedCategory"));
        assertEquals("DE", enrichment.get("tenantRegion"));
    }

    @Test
    @DisplayName("generateSmartLightbox selects catalog assets based on prompt")
    void testGenerateSmartLightbox() {
        MediaAsset a1 = new MediaAsset("SKU-1", "bolt1.png", "path1", "IMAGE_PNG", 100L, 100, 100, List.of("bolt"));
        a1.setId("id-1");
        MediaAsset a2 = new MediaAsset("SKU-2", "tool1.png", "path2", "IMAGE_PNG", 100L, 100, 100, List.of("tool"));
        a2.setId("id-2");

        Map<String, Object> lbResult = geminiAiService.generateSmartLightbox("automotive tools", List.of(a1, a2), "user-123");
        assertNotNull(lbResult);
        assertTrue(lbResult.containsKey("title"));
        assertTrue(lbResult.containsKey("selectedAssetIds"));
        assertEquals("user-123", lbResult.get("ownerUserId"));
    }
}
