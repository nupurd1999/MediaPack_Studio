package com.nupoor.mediapack.controller;

import com.nupoor.mediapack.config.tenant.TenantContext;
import com.nupoor.mediapack.model.CollaborativeLightbox;
import com.nupoor.mediapack.model.MediaAsset;
import com.nupoor.mediapack.service.CollaborativeLightboxService;
import com.nupoor.mediapack.service.GeminiAiService;
import com.nupoor.mediapack.service.MediaAssetService;
import com.nupoor.mediapack.service.SolrSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller exposing Gemini API (Gemma 4 31B) features:
 *
 * 1. GET  /api/ai/status             - API key health check & configuration status
 * 2. POST /api/ai/enrich-asset       - Multi-lingual tag & metadata auto-enrichment
 * 3. GET  /api/ai/smart-search       - Natural language to Solr search translation & execution
 * 4. POST /api/ai/generate-lightbox  - Prompt-driven "Smart Lightbox" collection creation
 * 5. POST /api/ai/generate-manifest  - Package export manifest & license documentation generator
 */
@RestController
@RequestMapping("/api/ai")
public class GeminiAiController {

    private final GeminiAiService geminiAiService;
    private final MediaAssetService assetService;
    private final SolrSearchService solrSearchService;
    private final CollaborativeLightboxService lightboxService;

    public GeminiAiController(
            GeminiAiService geminiAiService,
            MediaAssetService assetService,
            SolrSearchService solrSearchService,
            CollaborativeLightboxService lightboxService) {
        this.geminiAiService = geminiAiService;
        this.assetService = assetService;
        this.solrSearchService = solrSearchService;
        this.lightboxService = lightboxService;
    }

    /**
     * API Status Endpoint
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAiStatus() {
        return ResponseEntity.ok(geminiAiService.getStatus());
    }

    /**
     * Feature 1: Multi-Lingual Auto-Tagging & Metadata Enrichment
     */
    @PostMapping("/enrich-asset")
    public ResponseEntity<Map<String, Object>> enrichAsset(
            @RequestBody(required = false) MediaAsset inputAsset,
            @RequestParam(required = false) String assetId,
            @RequestParam(defaultValue = "false") boolean saveEnriched) {

        String tenantRegion = TenantContext.getCurrentTenant();
        MediaAsset targetAsset = inputAsset;

        if (assetId != null && !assetId.isBlank()) {
            Optional<MediaAsset> existing = assetService.getAssetById(assetId);
            if (existing.isPresent()) {
                targetAsset = existing.get();
            }
        }

        if (targetAsset == null) {
            targetAsset = new MediaAsset("SKU-MP-8821", "Hex Head Anchor Bolt.png", "assets/bolt.png", "IMAGE_PNG", 2048000, 3840, 2160, List.of("fastener"));
        } else {
            if (targetAsset.getSku() == null || targetAsset.getSku().isBlank()) targetAsset.setSku("SKU-MP-8821");
            if (targetAsset.getFileName() == null || targetAsset.getFileName().isBlank()) targetAsset.setFileName("Hex Head Anchor Bolt.png");
            if (targetAsset.getFileType() == null || targetAsset.getFileType().isBlank()) targetAsset.setFileType("IMAGE_PNG");
        }

        Map<String, Object> enrichedData = geminiAiService.enrichAssetMetadata(targetAsset, tenantRegion);


        if (saveEnriched && assetId != null && targetAsset.getId() != null) {
            List<String> newTags = (List<String>) enrichedData.get("tags");
            if (newTags != null) {
                targetAsset.setTags(newTags);
                assetService.saveAsset(targetAsset); // Saves to Mongo & re-indexes in Solr
                enrichedData.put("savedToCatalog", true);
            }
        }

        return ResponseEntity.ok(enrichedData);
    }

    /**
     * Feature 2: Natural Language to Solr Search Query Parsing & Execution
     */
    @GetMapping("/smart-search")
    public ResponseEntity<Map<String, Object>> smartSearch(
            @RequestParam String prompt,
            @RequestParam(defaultValue = "50") int rows) {

        // 1. Translate natural language prompt to Solr parameters via Gemma 4 31B
        Map<String, Object> translation = geminiAiService.translateNaturalLanguageToSolr(prompt);

        String queryTerm = (String) translation.getOrDefault("queryTerm", "*:*");
        String fileType = (String) translation.getOrDefault("fileType", "");
        String tag = (String) translation.getOrDefault("tag", "");

        // 2. Execute structured search query against Apache Solr index
        Map<String, Object> searchResults = solrSearchService.searchAssets(queryTerm, fileType, tag, rows);

        // 3. Return aggregated AI translation metadata + Solr search results
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("originalPrompt", prompt);
        response.put("aiTranslation", translation);
        response.put("tenantRegion", TenantContext.getCurrentTenant());
        response.put("searchResults", searchResults);

        return ResponseEntity.ok(response);
    }

    /**
     * Feature 3: AI-Powered "Smart Lightbox" Campaign Builder
     */
    @PostMapping("/generate-lightbox")
    public ResponseEntity<Map<String, Object>> generateSmartLightbox(
            @RequestParam String prompt,
            @RequestParam(defaultValue = "admin") String ownerUserId) {

        List<MediaAsset> availableAssets = assetService.getAllAssets();
        Map<String, Object> aiResult = geminiAiService.generateSmartLightbox(prompt, availableAssets, ownerUserId);

        String title = (String) aiResult.getOrDefault("title", "AI Curated Collection");
        List<String> selectedIds = (List<String>) aiResult.getOrDefault("selectedAssetIds", Collections.emptyList());

        // Create new CollaborativeLightbox in MongoDB catalog
        CollaborativeLightbox lightbox = lightboxService.createLightbox(title, ownerUserId);
        for (String id : selectedIds) {
            try {
                lightboxService.addAssetToLightbox(lightbox.getId(), id);
            } catch (Exception ignored) {
                // Graceful skip if asset ID invalid
            }
        }

        CollaborativeLightbox updated = lightboxService.getLightboxById(lightbox.getId()).orElse(lightbox);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("aiResult", aiResult);
        response.put("createdLightbox", updated);
        response.put("tenantRegion", TenantContext.getCurrentTenant());

        return ResponseEntity.ok(response);
    }

    /**
     * Feature 4: Automated Export Manifest & Licensing Summarizer
     */
    @PostMapping("/generate-manifest")
    public ResponseEntity<Map<String, String>> generateManifest(@RequestBody Map<String, Object> payload) {
        List<String> assetKeys = (List<String>) payload.get("assetKeys");
        String tenantRegion = TenantContext.getCurrentTenant();

        String manifestMarkdown = geminiAiService.generateExportManifest(assetKeys, tenantRegion);

        return ResponseEntity.ok(Map.of(
            "tenantRegion", tenantRegion,
            "manifest", manifestMarkdown
        ));
    }
}
