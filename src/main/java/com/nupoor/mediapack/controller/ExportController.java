package com.nupoor.mediapack.controller;

import com.nupoor.mediapack.config.tenant.TenantContext;
import com.nupoor.mediapack.model.ExportJobRequest;
import com.nupoor.mediapack.model.ExportJobState;
import com.nupoor.mediapack.service.AsyncMediaPackagerWorker;
import com.nupoor.mediapack.service.RedisTaskPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for triggering and monitoring async ZIP export jobs.
 *
 * POST /api/exports/trigger   — Enqueues job to Redis + kicks off async worker
 * GET  /api/exports/{jobId}   — Returns persisted Redis job state
 */
@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private final AsyncMediaPackagerWorker exportWorker;
    private final RedisTaskPublisher redisTaskPublisher;

    public ExportController(AsyncMediaPackagerWorker exportWorker, RedisTaskPublisher redisTaskPublisher) {
        this.exportWorker = exportWorker;
        this.redisTaskPublisher = redisTaskPublisher;
    }

    /**
     * Triggers an async ZIP export job.
     * 1. Generates or accepts a job ID
     * 2. Persists initial state to Redis Hash
     * 3. Enqueues job ID to Redis LIST queue
     * 4. Fires async worker (non-blocking)
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> triggerExportJob(@RequestBody ExportJobRequest request) {
        String jobId = (request.jobId() != null && !request.jobId().isBlank())
            ? request.jobId()
            : UUID.randomUUID().toString();

        String currentTenant = TenantContext.getCurrentTenant();

        // Persist job state to Redis before firing async worker
        ExportJobState state = redisTaskPublisher.enqueue(request, jobId, currentTenant);

        // Fire non-blocking async export worker
        exportWorker.processExportJob(jobId, request.assetKeys());

        return ResponseEntity.accepted().body(Map.of(
            "jobId",          jobId,
            "tenantRegion",   currentTenant,
            "status",         state.getStatus(),
            "progressTopic",  "/topic/export-progress/" + jobId,
            "statusEndpoint", "/api/exports/" + jobId,
            "message",        "Export job enqueued. Connect to STOMP broker or poll statusEndpoint."
        ));
    }

    /**
     * Polls persisted Redis state for a given job ID.
     * Useful for clients that cannot maintain a WebSocket connection.
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        return redisTaskPublisher.getJobState(jobId)
            .map(state -> ResponseEntity.ok().body(state))
            .orElse(ResponseEntity.notFound().build());
    }
}
