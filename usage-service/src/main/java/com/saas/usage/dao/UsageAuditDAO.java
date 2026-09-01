package com.saas.usage.dao;

import com.saas.usage.entity.UsageAuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class UsageAuditDAO {

    @Transactional
    public void log(UUID tenantId, UUID userId, String moduleSlug, String metricCode,
                     String action, Map<String, Object> metadata) {
        UsageAuditLog log = new UsageAuditLog();
        log.tenantId = tenantId;
        log.userId = userId;
        log.moduleSlug = moduleSlug;
        log.metricCode = metricCode;
        log.action = action;
        log.metadata = metadata != null ? metadata : Map.of();
        log.persist();
    }

    public long countByTenantAndModule(UUID tenantId, String moduleSlug) {
        if (moduleSlug == null) {
            return UsageAuditLog.count("tenantId", tenantId);
        }
        return UsageAuditLog.count("tenantId = ?1 and moduleSlug = ?2", tenantId, moduleSlug);
    }

    @SuppressWarnings("unchecked")
    public java.util.List<UsageAuditLog> list(UUID tenantId, String moduleSlug, int page, int pageSize) {
        var query = moduleSlug == null
                ? UsageAuditLog.find("tenantId = ?1 order by createdAt desc", tenantId)
                : UsageAuditLog.find("tenantId = ?1 and moduleSlug = ?2 order by createdAt desc", tenantId, moduleSlug);
        return query.page(page, pageSize).list();
    }
}
