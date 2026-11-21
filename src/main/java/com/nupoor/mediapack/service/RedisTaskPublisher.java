package com.nupoor.mediapack.service;

import com.nupoor.mediapack.config.tenant.TenantContext;
import com.nupoor.mediapack.model.ExportJobRequest;
import com.nupoor.mediapack.model.ExportJobState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Redis-backed export task queue and persistent job state.
 *
 * Fully tenant-namespaced:
 * 1. LIST   "mediapack:{tenant}:export:jobs"    — FIFO queue of pending job IDs per tenant
 * 2. HASH   "export:{tenant}:job:{jobId}"       — Per-job state metadata
 *
 * Jobs expire from Redis after 24 hours.
 */
@Service
public class RedisTaskPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisTaskPublisher.class);

    static final String QUEUE_KEY_BASE = "mediapack:export:jobs";
    static final String JOB_KEY_PREFIX = "export:job:";
    static final Duration JOB_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, Map<Object, Object>> inMemoryState = new ConcurrentHashMap<>();

    public RedisTaskPublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String getQueueKey(String tenant) {
        return "mediapack:" + (tenant != null ? tenant : "DE") + ":export:jobs";
    }

    private String getJobKey(String tenant, String jobId) {
        return "export:" + (tenant != null ? tenant : "DE") + ":job:" + jobId;
    }

    /**
     * Persists job state to Redis and enqueues the job ID for async pickup.
     */
    public ExportJobState enqueue(ExportJobRequest request, String jobId, String tenantRegion) {
        String effectiveTenant = (tenantRegion != null && !tenantRegion.isBlank()) ? tenantRegion : "DE";
        java.util.List<String> assetKeys = (request != null && request.assetKeys() != null) ? request.assetKeys() : java.util.Collections.emptyList();
        ExportJobState state = new ExportJobState(jobId, effectiveTenant, assetKeys);

        Map<Object, Object> stateMap = new java.util.HashMap<>();
        stateMap.put("jobId", state.getJobId());
        stateMap.put("tenantRegion", state.getTenantRegion());
        stateMap.put("status", state.getStatus());
        stateMap.put("progress", String.valueOf(state.getProgressPercent()));
        stateMap.put("submittedAt", state.getSubmittedAt().toString());
        if (assetKeys != null && !assetKeys.isEmpty()) {
            stateMap.put("assetKeys", String.join(",", assetKeys));
        }

        inMemoryState.put(jobId, stateMap);

        try {
            String tenantStateKey = getJobKey(effectiveTenant, jobId);
            String legacyStateKey = JOB_KEY_PREFIX + jobId;

            redisTemplate.opsForHash().putAll(tenantStateKey, stateMap);
            redisTemplate.expire(tenantStateKey, JOB_TTL);

            // Also keep legacy key alias for direct lookup compatibility
            redisTemplate.opsForHash().putAll(legacyStateKey, stateMap);
            redisTemplate.expire(legacyStateKey, JOB_TTL);

            // Enqueue to tenant-specific list
            redisTemplate.opsForList().leftPush(getQueueKey(effectiveTenant), jobId);
            redisTemplate.opsForList().leftPush(QUEUE_KEY_BASE, jobId);

            log.info("Enqueued export job {} (tenant={}) to Redis", jobId, effectiveTenant);
        } catch (Exception e) {
            log.warn("Redis unavailable for job {}. Falling back to in-memory state: {}", jobId, e.getMessage());
        }

        return state;
    }

    public void updateJobState(String jobId, String status, int progress, String downloadUrl, String errorMsg) {
        updateJobState(jobId, status, progress, downloadUrl, errorMsg, TenantContext.getCurrentTenant());
    }

    public void updateJobState(String jobId, String status, int progress, String downloadUrl, String errorMsg, String tenantRegion) {
        Map<Object, Object> stateMap = inMemoryState.computeIfAbsent(jobId, k -> new java.util.HashMap<>());
        stateMap.put("status", status);
        stateMap.put("progress", String.valueOf(progress));
        if (downloadUrl != null) stateMap.put("downloadUrl", downloadUrl);
        if (errorMsg != null) stateMap.put("errorMessage", errorMsg);
        if ("COMPLETED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
            stateMap.put("completedAt", java.time.Instant.now().toString());
        }

        try {
            String effectiveTenant = (tenantRegion != null && !tenantRegion.isBlank()) ? tenantRegion : "DE";
            String tenantStateKey = getJobKey(effectiveTenant, jobId);
            String legacyStateKey = JOB_KEY_PREFIX + jobId;

            for (String key : new String[]{tenantStateKey, legacyStateKey}) {
                redisTemplate.opsForHash().put(key, "status", status);
                redisTemplate.opsForHash().put(key, "progress", String.valueOf(progress));
                if (downloadUrl != null) redisTemplate.opsForHash().put(key, "downloadUrl", downloadUrl);
                if (errorMsg != null) redisTemplate.opsForHash().put(key, "errorMessage", errorMsg);
                if ("COMPLETED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
                    redisTemplate.opsForHash().put(key, "completedAt", java.time.Instant.now().toString());
                }
            }
        } catch (Exception e) {
            log.debug("Redis update ignored for job {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Retrieves full persisted job state from Redis, checking tenant namespace then fallback.
     */
    public Optional<Map<Object, Object>> getJobState(String jobId) {
        String currentTenant = TenantContext.getCurrentTenant();

        // 1. Try tenant namespaced key
        try {
            String tenantStateKey = getJobKey(currentTenant, jobId);
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(tenantStateKey);
            if (entries != null && !entries.isEmpty()) return Optional.of(entries);

            // 2. Try legacy direct key
            String legacyStateKey = JOB_KEY_PREFIX + jobId;
            entries = redisTemplate.opsForHash().entries(legacyStateKey);
            if (entries != null && !entries.isEmpty()) return Optional.of(entries);

            // 3. Scan other regional keys
            for (String region : new String[]{"DE", "NL", "US"}) {
                entries = redisTemplate.opsForHash().entries(getJobKey(region, jobId));
                if (entries != null && !entries.isEmpty()) return Optional.of(entries);
            }
        } catch (Exception e) {
            log.debug("Redis read fallback for job {}: {}", jobId, e.getMessage());
        }

        // Fallback to in-memory state
        return Optional.ofNullable(inMemoryState.get(jobId));
    }
}
