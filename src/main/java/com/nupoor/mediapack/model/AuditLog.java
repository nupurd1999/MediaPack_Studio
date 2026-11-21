package com.nupoor.mediapack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Immutable audit trail document for compliance and governance (GDPR Art. 30, BSI Grundschutz).
 * Records who accessed, uploaded, modified, or exported assets per tenant.
 */
@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String userId;
    private String action; // e.g. "ASSET_UPLOADED", "ASSET_DOWNLOADED", "EXPORT_TRIGGERED", "EXPORT_DOWNLOADED", "LIGHTBOX_EDITED"
    private String resourceId;
    private String details;
    private String clientIp;
    private Instant timestamp;

    public AuditLog() {
        this.timestamp = Instant.now();
    }

    public AuditLog(String tenantId, String userId, String action, String resourceId, String details, String clientIp) {
        this.tenantId = tenantId;
        this.userId = userId != null ? userId : "anonymous";
        this.action = action;
        this.resourceId = resourceId;
        this.details = details;
        this.clientIp = clientIp != null ? clientIp : "127.0.0.1";
        this.timestamp = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
