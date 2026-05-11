package com.saas.service;

import com.saas.entity.AuditLog;
import com.saas.security.TenantContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AuditService {

    @Transactional
    public void log(TenantContext ctx, String action, String resource,
                    String resourceId, Map<String, Object> metadata, String ipAddress) {
        AuditLog log = new AuditLog();
        log.tenantId = ctx.getTenantId();
        log.userId = ctx.getUserId();
        log.action = action;
        log.resource = resource;
        log.resourceId = resourceId;
        log.metadata = metadata != null ? metadata : Map.of();
        log.ipAddress = ipAddress;
        log.persist();
    }

    public void log(TenantContext ctx, String action) {
        log(ctx, action, null, null, null, null);
    }

    public void log(UUID tenantId, UUID userId, String action, String resource,
                    String resourceId, String ipAddress) {
        AuditLog log = new AuditLog();
        log.tenantId = tenantId;
        log.userId = userId;
        log.action = action;
        log.resource = resource;
        log.resourceId = resourceId;
        log.persist();
    }
}
