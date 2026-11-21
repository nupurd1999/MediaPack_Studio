package com.nupoor.mediapack.service;

import com.nupoor.mediapack.config.tenant.TenantContext;
import com.nupoor.mediapack.model.AuditLog;
import com.nupoor.mediapack.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for logging enterprise compliance activities (GDPR / BSI standard).
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an audit event asynchronously to avoid adding latency to critical user paths.
     */
    @Async("exportTaskExecutor")
    public void logEvent(String tenantId, String userId, String action, String resourceId, String details, String clientIp) {
        try {
            String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : TenantContext.getCurrentTenant();
            AuditLog auditLog = new AuditLog(effectiveTenant, userId, action, resourceId, details, clientIp);
            auditLogRepository.save(auditLog);
            log.info("[Audit] {} - Action: {} on Resource: {} by User: {} (Tenant: {})",
                    effectiveTenant, action, resourceId, userId, effectiveTenant);
        } catch (Exception e) {
            log.warn("[Audit] Could not save audit log: {}", e.getMessage());
        }
    }

    public List<AuditLog> getRecentLogs(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            return auditLogRepository.findTop50ByTenantIdOrderByTimestampDesc(tenantId);
        }
        return auditLogRepository.findTop50ByOrderByTimestampDesc();
    }
}
