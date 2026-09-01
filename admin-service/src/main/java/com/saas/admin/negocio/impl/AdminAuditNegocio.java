package com.saas.admin.negocio.impl;

import java.util.Map;

/**
 * Auditoria administrativa: registra quem fez qual mutação em qual recurso
 * (tenants, usuários admin, níveis de acesso, assinaturas), responsabilidade
 * do Admin Service (distinta da auditoria de uso de módulo, que vive no
 * Usage Service).
 */
public interface AdminAuditNegocio {

    void log(String actorUserId, String action, String resource, String resourceId, Map<String, Object> metadata);
}
