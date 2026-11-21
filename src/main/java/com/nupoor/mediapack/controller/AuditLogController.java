package com.nupoor.mediapack.controller;

import com.nupoor.mediapack.config.tenant.TenantContext;
import com.nupoor.mediapack.model.AuditLog;
import com.nupoor.mediapack.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for retrieving enterprise audit logs for compliance, security, and traceability.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAuditLogs() {
        String currentTenant = TenantContext.getCurrentTenant();
        List<AuditLog> logs = auditLogService.getRecentLogs(currentTenant);
        return ResponseEntity.ok(Map.of(
            "tenantRegion", currentTenant,
            "count", logs.size(),
            "logs", logs
        ));
    }
}
