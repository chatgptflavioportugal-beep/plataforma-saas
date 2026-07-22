package com.saas.subscription.service;

import com.saas.subscription.entity.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AuditService {

    public void log(UUID tenantId, UUID userId, String action, String resource,
                    String resourceId, String ipAddress) {
        log(tenantId, userId, action, resource, resourceId, null, ipAddress);
    }

    @Transactional
    public void log(UUID tenantId, UUID userId, String action, String resource,
                    String resourceId, Map<String, Object> metadata, String ipAddress) {
        AuditLog log = new AuditLog();
        log.tenantId = tenantId;
        log.userId = userId;
        log.action = action;
        log.resource = resource;
        log.resourceId = resourceId;
        log.metadata = metadata != null ? metadata : Map.of();
        log.ipAddress = ipAddress;
        log.persist();
    }
}
