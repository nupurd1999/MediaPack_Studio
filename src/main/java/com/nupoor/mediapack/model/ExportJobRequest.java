package com.nupoor.mediapack.model;

import java.util.List;

public class ExportJobRequest {

    private String jobId;
    private String lightboxId;
    private List<String> assetKeys;
    private String tenantRegion;

    public ExportJobRequest() {}

    public ExportJobRequest(String jobId, String lightboxId, List<String> assetKeys, String tenantRegion) {
        this.jobId = jobId;
        this.lightboxId = lightboxId;
        this.assetKeys = assetKeys;
        this.tenantRegion = tenantRegion;
    }

    public String jobId() { return jobId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String lightboxId() { return lightboxId; }
    public String getLightboxId() { return lightboxId; }
    public void setLightboxId(String lightboxId) { this.lightboxId = lightboxId; }

    public List<String> assetKeys() { return assetKeys; }
    public List<String> getAssetKeys() { return assetKeys; }
    public void setAssetKeys(List<String> assetKeys) { this.assetKeys = assetKeys; }

    public String tenantRegion() { return tenantRegion; }
    public String getTenantRegion() { return tenantRegion; }
    public void setTenantRegion(String tenantRegion) { this.tenantRegion = tenantRegion; }
}
