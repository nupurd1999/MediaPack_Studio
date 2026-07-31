package com.nupoor.mediapack.model;

import java.time.Instant;
import java.util.List;

/**
 * Persistent export job state stored in Redis Hash (export:job:{jobId}).
 * Tracks lifecycle from SUBMITTED → PROCESSING → COMPLETED / FAILED.
 */
public class ExportJobState {

    private String jobId;
    private String tenantRegion;
    private List<String> assetKeys;
    private String status;       // SUBMITTED, PROCESSING, COMPLETED, FAILED
    private int progressPercent;
    private String downloadUrl;
    private String errorMessage;
    private Instant submittedAt;
    private Instant completedAt;

    public ExportJobState() {}

    public ExportJobState(String jobId, String tenantRegion, List<String> assetKeys) {
        this.jobId = jobId;
        this.tenantRegion = tenantRegion;
        this.assetKeys = assetKeys;
        this.status = "SUBMITTED";
        this.progressPercent = 0;
        this.submittedAt = Instant.now();
    }

    // Getters and Setters
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getTenantRegion() { return tenantRegion; }
    public void setTenantRegion(String tenantRegion) { this.tenantRegion = tenantRegion; }

    public List<String> getAssetKeys() { return assetKeys; }
    public void setAssetKeys(List<String> assetKeys) { this.assetKeys = assetKeys; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getProgressPercent() { return progressPercent; }
    public void setProgressPercent(int progressPercent) { this.progressPercent = progressPercent; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
