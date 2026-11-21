package com.nupoor.mediapack.repository;

import com.nupoor.mediapack.model.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findTop50ByTenantIdOrderByTimestampDesc(String tenantId);
    List<AuditLog> findTop50ByOrderByTimestampDesc();
}
