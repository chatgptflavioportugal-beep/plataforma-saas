package com.saas.admin.service;

import com.saas.admin.entity.AdminAuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Auditoria administrativa: registra quem fez qual mutação em qual recurso
 * (tenants, usuários admin, níveis de acesso, assinaturas), responsabilidade
 * do Admin Service (distinta da auditoria de uso de módulo, que vive no
 * Usage Service).
 */
@ApplicationScoped
public class AdminAuditService {

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
