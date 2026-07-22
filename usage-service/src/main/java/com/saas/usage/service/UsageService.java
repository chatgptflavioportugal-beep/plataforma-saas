package com.saas.usage.service;

import com.saas.usage.cache.UsageCacheProvider;
import com.saas.usage.dto.UsageIncrementResponse;
import com.saas.usage.dto.UsageSummaryItem;
import com.saas.usage.events.EventPublisher;
import com.saas.usage.events.UsageEvent;
import com.saas.usage.repository.UsageAuditRepository;
import com.saas.usage.repository.UsageCounterRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class UsageService {

    @Inject UsageCounterRepository counterRepository;
    @Inject UsageAuditRepository auditRepository;
    @Inject UsageCacheProvider cacheProvider;
    @Inject EventPublisher eventPublisher;

    /**
     * Verifica a quota (claim {@code limits} do ModuleAccessToken) e, se ainda houver espaço,
     * incrementa o contador do dia. Quando o limite não está presente no token, o consumo é
     * ilimitado (nenhuma quota configurada para esse plano/módulo).
     */
    public UsageIncrementResponse increment(UUID tenantId, UUID userId, String moduleSlug,
                                             String metricCode, long amount, Long limit) {
        LocalDate today = LocalDate.now();

        if (limit != null) {
            long currentCount = counterRepository.getCount(tenantId, moduleSlug, metricCode, today);
            if (currentCount + amount > limit) {
                return new UsageIncrementResponse(false, currentCount, limit, Math.max(0, limit - currentCount));
            }
        }

        long newCount = counterRepository.incrementAndGet(tenantId, moduleSlug, metricCode, today, amount);
        cacheProvider.incrementAndGet(cacheKey(tenantId, moduleSlug, metricCode, today), amount);
        eventPublisher.publish(new UsageEvent(tenantId, userId, moduleSlug, metricCode,
                "usage.incremented", Map.of("amount", amount), Instant.now()));

        Long remaining = limit != null ? Math.max(0, limit - newCount) : null;
        return new UsageIncrementResponse(true, newCount, limit, remaining);
    }

    public List<UsageSummaryItem> summary(UUID tenantId, String moduleSlug, String metricCode,
                                           LocalDate from, LocalDate to, int page, int pageSize) {
        return counterRepository.summary(tenantId, moduleSlug, metricCode, from, to, page, pageSize);
    }

    public long countSummary(UUID tenantId, String moduleSlug, String metricCode, LocalDate from, LocalDate to) {
        return counterRepository.countSummary(tenantId, moduleSlug, metricCode, from, to);
    }

    public void audit(UUID tenantId, UUID userId, String moduleSlug, String metricCode,
                       String action, Map<String, Object> metadata) {
        auditRepository.log(tenantId, userId, moduleSlug, metricCode, action, metadata);
        eventPublisher.publish(new UsageEvent(tenantId, userId, moduleSlug, metricCode, action, metadata, Instant.now()));
    }

    private String cacheKey(UUID tenantId, String moduleSlug, String metricCode, LocalDate periodDate) {
        return tenantId + ":" + moduleSlug + ":" + metricCode + ":" + periodDate;
    }
}
