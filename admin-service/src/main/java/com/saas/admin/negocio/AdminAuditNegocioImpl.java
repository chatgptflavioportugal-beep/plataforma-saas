package com.saas.admin.negocio;

import com.saas.admin.entity.AdminAuditLog;
import com.saas.admin.negocio.impl.AdminAuditNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AdminAuditNegocioImpl implements AdminAuditNegocio {

    @Override
    @Transactional
    public void log(String actorUserId, String action, String resource, String resourceId, Map<String, Object> metadata) {
        AdminAuditLog log = new AdminAuditLog();
        log.actorUserId = UUID.fromString(actorUserId);
        log.action = action;
        log.resource = resource;
        log.resourceId = resourceId;
        log.metadata = metadata != null ? metadata : Map.of();
        log.persist();
    }
}
