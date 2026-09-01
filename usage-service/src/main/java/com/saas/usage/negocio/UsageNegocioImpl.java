package com.saas.usage.negocio;

import com.saas.usage.cache.UsageCacheProvider;
import com.saas.usage.dao.UsageAuditDAO;
import com.saas.usage.dao.UsageCounterDAO;
import com.saas.usage.dto.UsageIncrementResponse;
import com.saas.usage.dto.UsageSummaryItem;
import com.saas.usage.events.EventPublisher;
import com.saas.usage.events.UsageEvent;
import com.saas.usage.negocio.impl.UsageNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class UsageNegocioImpl implements UsageNegocio {

    @Inject UsageCounterDAO counterDAO;
    @Inject UsageAuditDAO auditDAO;
    @Inject UsageCacheProvider cacheProvider;
    @Inject EventPublisher eventPublisher;

    /**
     * Verifica a quota (claim {@code limits} do ModuleAccessToken) e, se ainda houver espaço,
     * incrementa o contador do dia. Quando o limite não está presente no token, o consumo é
     * ilimitado (nenhuma quota configurada para esse plano/módulo).
     */
    @Override
    public UsageIncrementResponse increment(UUID tenantId, UUID userId, String moduleSlug,
                                             String metricCode, long amount, Long limit) {
        LocalDate today = LocalDate.now();

        if (limit != null) {
            long currentCount = counterDAO.getCount(tenantId, moduleSlug, metricCode, today);
            if (currentCount + amount > limit) {
                return new UsageIncrementResponse(false, currentCount, limit, Math.max(0, limit - currentCount));
            }
        }

        long newCount = counterDAO.incrementAndGet(tenantId, moduleSlug, metricCode, today, amount);
        cacheProvider.incrementAndGet(cacheKey(tenantId, moduleSlug, metricCode, today), amount);
        eventPublisher.publish(new UsageEvent(tenantId, userId, moduleSlug, metricCode,
                "usage.incremented", Map.of("amount", amount), Instant.now()));

        Long remaining = limit != null ? Math.max(0, limit - newCount) : null;
        return new UsageIncrementResponse(true, newCount, limit, remaining);
    }

    @Override
    public List<UsageSummaryItem> summary(UUID tenantId, String moduleSlug, String metricCode,
                                           LocalDate from, LocalDate to, int page, int pageSize) {
        return counterDAO.summary(tenantId, moduleSlug, metricCode, from, to, page, pageSize);
    }

    @Override
    public long countSummary(UUID tenantId, String moduleSlug, String metricCode, LocalDate from, LocalDate to) {
        return counterDAO.countSummary(tenantId, moduleSlug, metricCode, from, to);
    }

    @Override
    public void audit(UUID tenantId, UUID userId, String moduleSlug, String metricCode,
                       String action, Map<String, Object> metadata) {
        auditDAO.log(tenantId, userId, moduleSlug, metricCode, action, metadata);
        eventPublisher.publish(new UsageEvent(tenantId, userId, moduleSlug, metricCode, action, metadata, Instant.now()));
    }

    private String cacheKey(UUID tenantId, String moduleSlug, String metricCode, LocalDate periodDate) {
        return tenantId + ":" + moduleSlug + ":" + metricCode + ":" + periodDate;
    }
}
