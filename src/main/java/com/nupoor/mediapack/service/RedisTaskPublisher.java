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

    public RedisTaskPublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Persists job state to Redis and enqueues the job ID for async pickup.
     */
    public ExportJobState enqueue(ExportJobRequest request, String jobId, String tenantRegion) {
        ExportJobState state = new ExportJobState(jobId, tenantRegion, request.assetKeys());

        String stateKey = JOB_KEY_PREFIX + jobId;
        redisTemplate.opsForHash().putAll(stateKey, Map.of(
            "jobId",         state.getJobId(),
            "tenantRegion",  state.getTenantRegion(),
            "status",        state.getStatus(),
            "progress",      String.valueOf(state.getProgressPercent()),
            "submittedAt",   state.getSubmittedAt().toString()
        ));
        redisTemplate.expire(stateKey, JOB_TTL);

        // Push job ID onto the FIFO queue
        redisTemplate.opsForList().leftPush(QUEUE_KEY, jobId);

        log.info("Enqueued export job {} (tenant={}) with {} assets to Redis queue",
                jobId, tenantRegion, request.assetKeys().size());

        return state;
    }

    /**
     * Updates the persisted job state hash in Redis with new status/progress.
     */
    public void updateJobState(String jobId, String status, int progress, String downloadUrl, String errorMsg) {
        String stateKey = JOB_KEY_PREFIX + jobId;

        redisTemplate.opsForHash().put(stateKey, "status", status);
        redisTemplate.opsForHash().put(stateKey, "progress", String.valueOf(progress));

        if (downloadUrl != null) {
            redisTemplate.opsForHash().put(stateKey, "downloadUrl", downloadUrl);
        }
        if (errorMsg != null) {
            redisTemplate.opsForHash().put(stateKey, "errorMessage", errorMsg);
        }
    }

    /**
     * Retrieves full persisted job state from Redis.
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<Object, Object>> getJobState(String jobId) {
        String stateKey = JOB_KEY_PREFIX + jobId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(stateKey);
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries);
    }
}
