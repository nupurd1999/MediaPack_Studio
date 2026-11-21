package com.nupoor.mediapack.service;

import com.nupoor.mediapack.config.tenant.TenantContext;
import com.nupoor.mediapack.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Async media packaging worker — the core of the export pipeline.
 *
 * Execution flow per job:
 *  1. Mark job PROCESSING in Redis (namespaced per tenant)
 *  2. Stream each asset key into a ZipOutputStream
 *  3. Broadcast incremental WebSockets progress to /topic/export-progress/{jobId}
 *  4. Store finished ZIP in pluggable StorageService (Local Disk or S3)
 *  5. Generate client download URL
 *  6. Broadcast COMPLETED event with download URL
 *  7. Update final state in Redis Hash
 */
@Service
public class AsyncMediaPackagerWorker {

    private static final Logger log = LoggerFactory.getLogger(AsyncMediaPackagerWorker.class);
    private static final int MAX_ZIP_CACHE_ENTRIES = 50;
    private static final Map<String, byte[]> ZIP_CACHE = java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<>(MAX_ZIP_CACHE_ENTRIES, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > MAX_ZIP_CACHE_ENTRIES;
            }
        }
    );

    public static byte[] getZipBytes(String jobId) {
        return ZIP_CACHE.get(jobId);
    }

    private final SimpMessagingTemplate messagingTemplate;
    private final StorageService storageService;
    private final RedisTaskPublisher redisTaskPublisher;
    private final GeminiAiService geminiAiService;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    public AsyncMediaPackagerWorker(
            SimpMessagingTemplate messagingTemplate,
            StorageService storageService,
            RedisTaskPublisher redisTaskPublisher,
            GeminiAiService geminiAiService) {
        this.messagingTemplate = messagingTemplate;
        this.storageService = storageService;
        this.redisTaskPublisher = redisTaskPublisher;
        this.geminiAiService = geminiAiService;
    }

    @Async("exportTaskExecutor")
    public void processExportJob(String jobId, List<String> assetKeys) {
        processExportJob(jobId, assetKeys, "DE");
    }

    @Async("exportTaskExecutor")
    public void processExportJob(String jobId, List<String> assetKeys, String tenantRegion) {
        String effectiveRegion = (tenantRegion != null && !tenantRegion.isBlank()) ? tenantRegion : "DE";
        log.info("[Export {}] Starting async ZIP packaging — {} assets (tenant={}) on thread {}",
                jobId, assetKeys != null ? assetKeys.size() : 0, effectiveRegion, Thread.currentThread().getName());

        List<String> distinctKeys = (assetKeys != null)
                ? assetKeys.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList()
                : List.of();

        if (distinctKeys.isEmpty()) {
            String errMsg = "No asset keys provided for export.";
            redisTaskPublisher.updateJobState(jobId, "FAILED", 0, null, errMsg, effectiveRegion);
            broadcast(jobId, 0, "FAILED", null, errMsg);
            return;
        }

        // Set tenant context for worker thread
        TenantContext.setCurrentTenant(effectiveRegion);

        // Mark job as PROCESSING in Redis
        redisTaskPublisher.updateJobState(jobId, "PROCESSING", 0, null, null, effectiveRegion);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            int total = distinctKeys.size();

            for (int i = 0; i < total; i++) {
                String rawKey = distinctKeys.get(i);
                String safeFileKey = rawKey.replaceAll("[^a-zA-Z0-9_-]", "_");

                // Add asset entry into ZIP archive
                ZipEntry entry = new ZipEntry("assets/" + safeFileKey + ".dat");
                zos.putNextEntry(entry);
                String assetContent = String.format(
                    "=== Würth Industrial Media Asset ===\nKey: %s\nTenant Region: %s\nExported at: %s\nJob: %s",
                    rawKey, effectiveRegion, Instant.now(), jobId);
                zos.write(assetContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                int percent = (int) (((double) (i + 1) / total) * 100);

                // Update Redis state with current progress
                redisTaskPublisher.updateJobState(jobId, "PROCESSING", percent, null, null, effectiveRegion);

                // Broadcast live STOMP progress event
                broadcast(jobId, percent, "PROCESSING", null,
                    String.format("Packaged [%d/%d] — %s", i + 1, total, rawKey));

                // Simulated I/O latency between chunk writes
                Thread.sleep(150);
            }

            // Generate AI Package Manifest and add to ZIP
            try {
                String manifestContent = geminiAiService.generateExportManifest(distinctKeys, effectiveRegion);
                if (manifestContent == null || manifestContent.isBlank()) {
                    manifestContent = "# 📦 MediaPack Studio — Package Manifest\n\n**Tenant Region:** `" + effectiveRegion + "`\n";
                }
                ZipEntry manifestEntry = new ZipEntry("MANIFEST.md");
                zos.putNextEntry(manifestEntry);
                zos.write(manifestContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                log.info("[Export {}] Included AI generated MANIFEST.md in ZIP bundle", jobId);
            } catch (Exception ex) {
                log.warn("[Export {}] Could not generate AI manifest: {}", jobId, ex.getMessage());
            }

            zos.finish();

            byte[] zipBytes = baos.toByteArray();
            ZIP_CACHE.put(jobId, zipBytes);

            String storageKey = "exports/" + jobId + ".zip";
            String downloadUrl = "/api/exports/download/" + jobId;

            try {
                if (storageService != null) {
                    storageService.store(storageKey, new ByteArrayInputStream(zipBytes), zipBytes.length, "application/zip");
                    if (storageService instanceof S3StorageService s3) {
                        downloadUrl = s3.generatePresignedUrl(storageKey);
                    } else {
                        downloadUrl = storageService.getDownloadUrl(storageKey);
                    }
                }
            } catch (Exception storageEx) {
                log.warn("[Export {}] Primary storage failed, using direct endpoint fallback: {}", jobId, storageEx.getMessage());
                downloadUrl = "/api/exports/download/" + jobId;
            }

            // Persist final COMPLETED state in Redis
            redisTaskPublisher.updateJobState(jobId, "COMPLETED", 100, downloadUrl, null, effectiveRegion);

            if (auditLogService != null) {
                auditLogService.logEvent(effectiveRegion, "system", "EXPORT_COMPLETED", jobId,
                        "Packaged " + total + " assets (" + zipBytes.length + " bytes)", "127.0.0.1");
            }

            log.info("[Export {}] Completed — ZIP size: {} bytes — Download URL: {}", jobId, zipBytes.length, downloadUrl);

            // Final STOMP broadcast
            broadcast(jobId, 100, "COMPLETED", downloadUrl,
                String.format("ZIP packaging complete! %d assets bundled (%d bytes).", total, zipBytes.length));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Export {}] Interrupted during packaging", jobId);
            handleFailure(jobId, "Export job was interrupted.", effectiveRegion);
        } catch (Exception e) {
            log.error("[Export {}] Failed during packaging: {}", jobId, e.getMessage(), e);
            handleFailure(jobId, "Packaging failed: " + e.getMessage(), effectiveRegion);
        } finally {
            TenantContext.clear();
        }
    }

    private void handleFailure(String jobId, String message, String tenantRegion) {
        redisTaskPublisher.updateJobState(jobId, "FAILED", 0, null, message, tenantRegion);
        broadcast(jobId, 0, "FAILED", null, message);
    }

    private void broadcast(String jobId, int percent, String status, String downloadUrl, String message) {
        messagingTemplate.convertAndSend(
            "/topic/export-progress/" + jobId,
            new ExportProgressEvent(jobId, percent, status, downloadUrl, message)
        );
    }

    public static class ExportProgressEvent {
        private String jobId;
        private int progressPercent;
        private String status;
        private String downloadUrl;
        private String message;

        public ExportProgressEvent() {}

        public ExportProgressEvent(String jobId, int progressPercent, String status, String downloadUrl, String message) {
            this.jobId = jobId;
            this.progressPercent = progressPercent;
            this.status = status;
            this.downloadUrl = downloadUrl;
            this.message = message;
        }

        public String getJobId() { return jobId; }
        public int getProgressPercent() { return progressPercent; }
        public String getStatus() { return status; }
        public String getDownloadUrl() { return downloadUrl; }
        public String getMessage() { return message; }
    }
}
