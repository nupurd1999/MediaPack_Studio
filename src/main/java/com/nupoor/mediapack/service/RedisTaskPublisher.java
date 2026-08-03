package com.nupoor.mediapack.service;

import com.nupoor.mediapack.model.ExportJobRequest;
import com.nupoor.mediapack.model.ExportJobState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the Redis-backed export task queue and persistent job state.
 *
 * Two Redis data structures are used:
 * 1. LIST   "mediapack:export:jobs"            — FIFO queue of pending job IDs
 * 2. HASH   "export:job:{jobId}"               — Per-job state metadata
 *
 * Jobs expire from Redis after 24 hours (matching the S3 pre-signed URL TTL).
 */
@Service
public class RedisTaskPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisTaskPublisher.class);

    static final String QUEUE_KEY = "mediapack:export:jobs";
    static final String JOB_KEY_PREFIX = "export:job:";
    static final Duration JOB_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, Map<Object, Object>> inMemoryState = new java.util.concurrent.ConcurrentHashMap<>();

    public RedisTaskPublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Persists job state to Redis and enqueues the job ID for async pickup.
     */
    public ExportJobState enqueue(ExportJobRequest request, String jobId, String tenantRegion) {
        ExportJobState state = new ExportJobState(jobId, tenantRegion, request.assetKeys());

        Map<Object, Object> stateMap = new java.util.HashMap<>(Map.of(
            "jobId",         state.getJobId(),
            "tenantRegion",  state.getTenantRegion(),
            "status",        state.getStatus(),
            "progress",      String.valueOf(state.getProgressPercent()),
            "submittedAt",   state.getSubmittedAt().toString()
        ));

        inMemoryState.put(jobId, stateMap);

        try {
            String stateKey = JOB_KEY_PREFIX + jobId;
            redisTemplate.opsForHash().putAll(stateKey, stateMap);
            redisTemplate.expire(stateKey, JOB_TTL);
            redisTemplate.opsForList().leftPush(QUEUE_KEY, jobId);
            log.info("Enqueued export job {} (tenant={}) to Redis", jobId, tenantRegion);
        } catch (Exception e) {
            log.warn("Redis unavailable for job {}. Falling back to in-memory state: {}", jobId, e.getMessage());
        }

        return state;
    }

    /**
     * Updates the persisted job state hash in Redis with new status/progress.
     */
    public void updateJobState(String jobId, String status, int progress, String downloadUrl, String errorMsg) {
        Map<Object, Object> stateMap = inMemoryState.computeIfAbsent(jobId, k -> new java.util.HashMap<>());
        stateMap.put("status", status);
        stateMap.put("progress", String.valueOf(progress));
        if (downloadUrl != null) stateMap.put("downloadUrl", downloadUrl);
        if (errorMsg != null) stateMap.put("errorMessage", errorMsg);

        try {
            String stateKey = JOB_KEY_PREFIX + jobId;
            redisTemplate.opsForHash().put(stateKey, "status", status);
            redisTemplate.opsForHash().put(stateKey, "progress", String.valueOf(progress));
            if (downloadUrl != null) redisTemplate.opsForHash().put(stateKey, "downloadUrl", downloadUrl);
            if (errorMsg != null) redisTemplate.opsForHash().put(stateKey, "errorMessage", errorMsg);
        } catch (Exception e) {
            log.debug("Redis update ignored for job {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Retrieves full persisted job state from Redis.
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<Object, Object>> getJobState(String jobId) {
        try {
            String stateKey = JOB_KEY_PREFIX + jobId;
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(stateKey);
            if (!entries.isEmpty()) return Optional.of(entries);
        } catch (Exception e) {
            log.debug("Redis read fallback for job {}: {}", jobId, e.getMessage());
        }

        Map<Object, Object> fallback = inMemoryState.get(jobId);
        return fallback != null && !fallback.isEmpty() ? Optional.of(fallback) : Optional.empty();
    }
}

