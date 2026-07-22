package com.saas.usage.repository;

import com.saas.usage.dto.UsageSummaryItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UsageCounterRepository {

    @Inject
    EntityManager em;

    /** Upsert atômico: incrementa o contador do dia e retorna o novo total. */
    @Transactional
    public long incrementAndGet(UUID tenantId, String moduleSlug, String metricCode, LocalDate periodDate, long amount) {
        Object result = em.createNativeQuery("""
                INSERT INTO module_usage_counters (id, tenant_id, module_slug, metric_code, period_date, count, updated_at)
                VALUES (gen_random_uuid(), :tenantId, :moduleSlug, :metricCode, :periodDate, :amount, now())
                ON CONFLICT (tenant_id, module_slug, metric_code, period_date)
                DO UPDATE SET count = module_usage_counters.count + :amount, updated_at = now()
                RETURNING count
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("moduleSlug", moduleSlug)
                .setParameter("metricCode", metricCode)
                .setParameter("periodDate", periodDate)
                .setParameter("amount", amount)
                .getSingleResult();
        return ((Number) result).longValue();
    }

    public long getCount(UUID tenantId, String moduleSlug, String metricCode, LocalDate periodDate) {
        List<?> result = em.createNativeQuery("""
                SELECT count FROM module_usage_counters
                WHERE tenant_id = :tenantId AND module_slug = :moduleSlug
                  AND metric_code = :metricCode AND period_date = :periodDate
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("moduleSlug", moduleSlug)
                .setParameter("metricCode", metricCode)
                .setParameter("periodDate", periodDate)
                .getResultList();
        return result.isEmpty() ? 0L : ((Number) result.get(0)).longValue();
    }

    @SuppressWarnings("unchecked")
    public List<UsageSummaryItem> summary(UUID tenantId, String moduleSlug, String metricCode,
                                           LocalDate from, LocalDate to, int page, int pageSize) {
        var query = em.createNativeQuery("""
                SELECT module_slug, metric_code, period_date, count
                FROM module_usage_counters
                WHERE tenant_id = :tenantId
                  AND (:moduleSlug IS NULL OR module_slug = :moduleSlug)
                  AND (:metricCode IS NULL OR metric_code = :metricCode)
                  AND period_date BETWEEN :from AND :to
                ORDER BY period_date DESC
                OFFSET :offset LIMIT :limit
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("moduleSlug", moduleSlug)
                .setParameter("metricCode", metricCode)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("offset", page * pageSize)
                .setParameter("limit", pageSize);

        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(r -> new UsageSummaryItem(
                        (String) r[0],
                        (String) r[1],
                        ((java.sql.Date) r[2]).toLocalDate(),
                        ((Number) r[3]).longValue()
                ))
                .toList();
    }

    public long countSummary(UUID tenantId, String moduleSlug, String metricCode, LocalDate from, LocalDate to) {
        Object result = em.createNativeQuery("""
                SELECT COUNT(*) FROM module_usage_counters
                WHERE tenant_id = :tenantId
                  AND (:moduleSlug IS NULL OR module_slug = :moduleSlug)
                  AND (:metricCode IS NULL OR metric_code = :metricCode)
                  AND period_date BETWEEN :from AND :to
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("moduleSlug", moduleSlug)
                .setParameter("metricCode", metricCode)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return ((Number) result).longValue();
    }
}
