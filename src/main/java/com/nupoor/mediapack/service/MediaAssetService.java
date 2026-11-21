package com.nupoor.mediapack.service;

import com.nupoor.mediapack.config.tenant.TenantContext;
import com.nupoor.mediapack.model.MediaAsset;
import com.nupoor.mediapack.repository.MediaAssetRepository;
import com.nupoor.mediapack.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

@Service
public class MediaAssetService {

    private static final Logger log = LoggerFactory.getLogger(MediaAssetService.class);

    private final MediaAssetRepository assetRepository;
    private final SolrSearchService solrSearchService;
    private final StorageService storageService;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    public MediaAssetService(MediaAssetRepository assetRepository, SolrSearchService solrSearchService, StorageService storageService) {
        this.assetRepository = assetRepository;
        this.solrSearchService = solrSearchService;
        this.storageService = storageService;
    }

    public MediaAsset saveAsset(MediaAsset asset) {
        if (asset.getTenantRegion() == null || asset.getTenantRegion().isBlank()) {
            asset.setTenantRegion(TenantContext.getCurrentTenant());
        }
        MediaAsset saved = assetRepository.save(asset);
        solrSearchService.indexAsset(saved);
        return saved;
    }

    /**
     * Binary asset upload pipeline with SHA-256 deduplication.
     */
    public MediaAsset uploadAsset(MultipartFile file, String sku, String fileType, List<String> tags, String tenantRegion) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be null or empty");
        }
        String effectiveTenant = (tenantRegion != null && !tenantRegion.isBlank()) ? tenantRegion : TenantContext.getCurrentTenant();
        byte[] bytes = file.getBytes();
        String checksum = calculateSha256(bytes);

        // Deduplication check: Has this exact file been uploaded before?
        Optional<MediaAsset> existing = assetRepository.findByChecksumSha256(checksum);
        if (existing.isPresent()) {
            MediaAsset existingAsset = existing.get();
            log.info("[DAM Ingestion] Deduplicating asset (SHA-256 match: {}). Linking SKU {} to existing asset {}",
                    checksum, sku, existingAsset.getId());

            if (tags != null && !tags.isEmpty()) {
                List<String> mutableTags = new ArrayList<>(existingAsset.getTags());
                for (String tag : tags) {
                    if (!mutableTags.contains(tag)) {
                        mutableTags.add(tag);
                    }
                }
                existingAsset.setTags(mutableTags);
                assetRepository.save(existingAsset);
            }

            if (auditLogService != null) {
                auditLogService.logEvent(effectiveTenant, "system", "ASSET_DEDUPLICATED", existingAsset.getId(),
                        "Reused existing binary for SKU: " + sku + " (SHA-256: " + checksum + ")", "127.0.0.1");
            }
            return existingAsset;
        }

        // New asset storage
        String assetId = UUID.randomUUID().toString();
        String rawFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "asset.dat";
        String originalFilename = java.nio.file.Paths.get(rawFilename).getFileName().toString();
        String storageKey = "assets/" + effectiveTenant.toLowerCase() + "/" + assetId + "/" + originalFilename;

        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        storageService.store(storageKey, new ByteArrayInputStream(bytes), bytes.length, contentType);

        int width = 0;
        int height = 0;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) {
                width = image.getWidth();
                height = image.getHeight();
            }
        } catch (Exception ignored) {}

        String effectiveSku = (sku != null && !sku.isBlank()) ? sku : "SKU-" + assetId.substring(0, 8).toUpperCase();
        String effectiveFileType = (fileType != null && !fileType.isBlank()) ? fileType : mapContentTypeToFileType(contentType, originalFilename);

        MediaAsset newAsset = new MediaAsset();
        newAsset.setId(assetId);
        newAsset.setSku(effectiveSku);
        newAsset.setFileName(originalFilename);
        newAsset.setOriginalFileName(originalFilename);
        newAsset.setS3Key(storageKey);
        newAsset.setStoragePath(storageKey);
        newAsset.setFileType(effectiveFileType);
        newAsset.setSizeInBytes(bytes.length);
        newAsset.setWidth(width);
        newAsset.setHeight(height);
        newAsset.setChecksumSha256(checksum);
        newAsset.setTenantRegion(effectiveTenant);
        if (tags != null) newAsset.setTags(new ArrayList<>(tags));

        MediaAsset saved = assetRepository.save(newAsset);
        solrSearchService.indexAsset(saved);

        if (auditLogService != null) {
            auditLogService.logEvent(effectiveTenant, "system", "ASSET_UPLOADED", saved.getId(),
                    "Uploaded " + originalFilename + " (" + bytes.length + " bytes, SHA-256: " + checksum + ")", "127.0.0.1");
        }

        return saved;
    }

    public Optional<MediaAsset> getAssetById(String id) {
        return assetRepository.findById(id);
    }

    public List<MediaAsset> getAllAssets() {
        return assetRepository.findAll();
    }

    public List<MediaAsset> getAssetsBySku(String sku) {
        return assetRepository.findBySku(sku);
    }

    public String getPresignedDownloadUrl(String assetId) {
        MediaAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found with ID: " + assetId));
        return storageService.getDownloadUrl(asset.getS3Key());
    }

    public InputStream getAssetInputStream(String assetId) {
        MediaAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found with ID: " + assetId));
        return storageService.getInputStream(asset.getS3Key());
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String mapContentTypeToFileType(String contentType, String filename) {
        if (contentType.contains("png") || filename.endsWith(".png")) return "IMAGE_PNG";
        if (contentType.contains("jpeg") || contentType.contains("jpg") || filename.endsWith(".jpg")) return "IMAGE_JPEG";
        if (contentType.contains("webp") || filename.endsWith(".webp")) return "IMAGE_WEBP";
        if (contentType.contains("pdf") || filename.endsWith(".pdf")) return "DOCUMENT_PDF";
        if (contentType.contains("video") || filename.endsWith(".mp4")) return "VIDEO_MP4";
        return "GENERIC_BINARY";
    }
}
