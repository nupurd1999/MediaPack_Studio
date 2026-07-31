package com.nupoor.mediapack.controller;

import com.nupoor.mediapack.config.tenant.TenantContext;
import com.nupoor.mediapack.model.MediaAsset;
import com.nupoor.mediapack.service.MediaAssetService;
import com.nupoor.mediapack.service.SolrSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
