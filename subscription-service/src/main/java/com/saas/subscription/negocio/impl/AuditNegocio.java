package com.saas.subscription.negocio.impl;

import java.util.Map;
import java.util.UUID;

public interface AuditNegocio {

    void log(UUID tenantId, UUID userId, String action, String resource,
             String resourceId, String ipAddress);

    void log(UUID tenantId, UUID userId, String action, String resource,
             String resourceId, Map<String, Object> metadata, String ipAddress);
}
