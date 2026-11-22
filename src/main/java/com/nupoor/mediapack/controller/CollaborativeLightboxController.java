package com.nupoor.mediapack.controller;

import com.nupoor.mediapack.config.tenant.TenantContext;
import com.nupoor.mediapack.model.CollaborativeLightbox;
import com.nupoor.mediapack.service.CollaborativeLightboxService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lightboxes")
public class CollaborativeLightboxController {

    private final CollaborativeLightboxService lightboxService;

    public CollaborativeLightboxController(CollaborativeLightboxService lightboxService) {
        this.lightboxService = lightboxService;
    }

    @PostMapping
    public ResponseEntity<CollaborativeLightbox> createLightbox(
            @RequestParam String title,
            @RequestParam String ownerUserId) {
        CollaborativeLightbox created = lightboxService.createLightbox(title, ownerUserId);
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllLightboxes() {
        List<CollaborativeLightbox> lightboxes = lightboxService.getLightboxesByOwner("ND");
        return ResponseEntity.ok(Map.of(
            "tenantRegion", TenantContext.getCurrentTenant(),
            "lightboxes", lightboxes
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CollaborativeLightbox> getLightboxById(@PathVariable String id) {
        return lightboxService.getLightboxById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{ownerUserId}")
    public ResponseEntity<Map<String, Object>> getLightboxesByOwner(@PathVariable String ownerUserId) {
        List<CollaborativeLightbox> lightboxes = lightboxService.getLightboxesByOwner(ownerUserId);
        return ResponseEntity.ok(Map.of(
            "tenantRegion", TenantContext.getCurrentTenant(),
            "lightboxes", lightboxes
        ));
    }

    @PostMapping("/{id}/assets/{assetId}")
    public ResponseEntity<?> addAsset(
            @PathVariable String id,
            @PathVariable String assetId) {
        try {
            CollaborativeLightbox updated = lightboxService.addAssetToLightbox(id, assetId);
            return ResponseEntity.ok(updated);
        } catch (OptimisticLockingFailureException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Concurrent edit detected on lightbox. Version conflict.",
                                 "exception", ex.getClass().getSimpleName()));
        }
    }

    @DeleteMapping("/{id}/assets/{assetId}")
    public ResponseEntity<?> removeAsset(
            @PathVariable String id,
            @PathVariable String assetId) {
        try {
            CollaborativeLightbox updated = lightboxService.removeAssetFromLightbox(id, assetId);
            return ResponseEntity.ok(updated);
        } catch (OptimisticLockingFailureException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Concurrent edit detected on lightbox. Version conflict.",
                                 "exception", ex.getClass().getSimpleName()));
        }
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<?> shareWithUser(
            @PathVariable String id,
            @RequestParam String userId) {
        CollaborativeLightbox updated = lightboxService.shareWithUser(id, userId);
        return ResponseEntity.ok(updated);
    }
}
