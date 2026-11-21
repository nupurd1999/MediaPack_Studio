package com.nupoor.mediapack.controller;

import com.nupoor.mediapack.config.tenant.TenantContext;
import com.nupoor.mediapack.model.MediaAsset;
import com.nupoor.mediapack.service.MediaAssetService;
import com.nupoor.mediapack.service.SolrSearchService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assets")
public class MediaAssetController {

    private final MediaAssetService assetService;
    private final SolrSearchService solrSearchService;

    public MediaAssetController(MediaAssetService assetService, SolrSearchService solrSearchService) {
        this.assetService = assetService;
        this.solrSearchService = solrSearchService;
    }

    @PostMapping
    public ResponseEntity<MediaAsset> createAsset(@RequestBody MediaAsset asset) {
        MediaAsset created = assetService.saveAsset(asset);
        return ResponseEntity.ok(created);
    }

    /**
     * Binary multipart asset upload with automatic SHA-256 deduplication and dimension extraction.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaAsset> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sku", required = false) String sku,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestParam(value = "tags", required = false) List<String> tags) throws IOException {
        String tenant = TenantContext.getCurrentTenant();
        MediaAsset created = assetService.uploadAsset(file, sku, fileType, tags, tenant);
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllAssets() {
        List<MediaAsset> assets = assetService.getAllAssets();
        return ResponseEntity.ok(Map.of(
            "tenantRegion", TenantContext.getCurrentTenant(),
            "count", assets.size(),
            "assets", assets
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MediaAsset> getAssetById(@PathVariable String id) {
        return assetService.getAssetById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download-url")
    public ResponseEntity<Map<String, String>> getDownloadUrl(@PathVariable String id) {
        String url = assetService.getPresignedDownloadUrl(id);
        return ResponseEntity.ok(Map.of(
            "assetId", id,
            "presignedDownloadUrl", url
        ));
    }

    /**
     * Directly streams the stored binary asset bytes with proper Content-Disposition.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadAssetBinary(@PathVariable String id) {
        MediaAsset asset = assetService.getAssetById(id)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found with ID: " + id));

        String rawFilename = asset.getFileName() != null ? asset.getFileName() : "asset-" + id + ".dat";
        String safeFilename = rawFilename.replace("\"", "").replace("\n", "").replace("\r", "");

        try (InputStream is = assetService.getAssetInputStream(id)) {
            byte[] bytes = is.readAllBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFilename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, asset.getFileType() != null && asset.getFileType().contains("IMAGE") ? "image/png" : "application/octet-stream")
                    .body(bytes);
        } catch (Exception e) {
            // Fallback for mocked catalog assets without real disk file
            byte[] placeholder = ("=== Würth Industrial Media Asset ===\nSKU: " + asset.getSku() + "\nFile: " + safeFilename)
                    .getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFilename + "\"")
                    .body(placeholder);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchAssets(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "50") int rows) {
        Map<String, Object> results = solrSearchService.searchAssets(q, fileType, tag, rows);
        return ResponseEntity.ok(results);
    }
}
