package com.saas.subscription.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Consulta agregada do Dashboard: status de acesso do perfil a todos os
 * módulos ativos, mais os serviços de cada módulo elegível. Mantida como
 * Native Query (CASE/EXTRACT EPOCH/subselects correlacionados sem
 * equivalente direto em JPQL, e uma única ida ao banco em vez de N+1
 * entidades). Object[] nunca sai desta classe.
 */
@ApplicationScoped
public class DashboardRepository {

    @Inject
    EntityManager em;

    public record ModuleRow(
        UUID moduleId, String moduleName, String moduleSlug, String moduleDescription, String moduleIconPath,
        UUID subscriptionId, String subscriptionStatus, OffsetDateTime subscriptionExpiresAt, boolean pastExpiry,
        String planName, String planSlug, UUID planVersionId, long serviceCount, boolean hasFreePlan,
        Integer trialDaysRemaining
    ) {}

    @SuppressWarnings("unchecked")
    public List<ModuleRow> listModulesWithAccessStatus(UUID tenantId) {
        List<Object[]> rows = em.createNativeQuery("""
            SELECT
              pm.id, pm.name, pm.slug, pm.description, pm.icon_path,
              pms.id               AS sub_id,
              pms.status           AS sub_status,
              pms.expires_at       AS sub_expires_at,
              (pms.status IN ('ACTIVE', 'TRIAL', 'TRIAL_CANCELLED') AND pms.expires_at IS NOT NULL AND pms.expires_at < NOW())
                                    AS sub_past_expiry,
              p.name               AS plan_name,
              p.code               AS plan_slug,
              pvm_sub.id           AS plan_version_id,
              (SELECT COUNT(*) FROM platform_module_services s
               WHERE s.module_id = pm.id AND s.is_active = TRUE) AS service_count,
              CASE WHEN EXISTS (
                SELECT 1 FROM plan_version_modules pvm2
                WHERE pvm2.module_id = pm.id
                  AND pvm2.status = 'active'
                  AND pvm2.monthly_price = 0
              ) THEN 1 ELSE 0 END  AS has_free_plan,
              CASE WHEN pms.status IN ('TRIAL', 'TRIAL_CANCELLED') AND pms.expires_at IS NOT NULL
                THEN GREATEST(0, CEIL(EXTRACT(EPOCH FROM (pms.expires_at - NOW())) / 86400.0))::int
                ELSE NULL END      AS trial_days_remaining
            FROM platform_modules pm
            LEFT JOIN profile_module_subscriptions pms
              ON pms.module_id = pm.id
             AND pms.tenant_id = :tenantId
             AND pms.status IN ('ACTIVE', 'TRIAL', 'TRIAL_CANCELLED', 'EXPIRED', 'PENDING_PAYMENT')
            LEFT JOIN plan_version_modules pvm_sub ON pvm_sub.id = pms.plan_version_id
            LEFT JOIN plans p ON p.id = pvm_sub.plan_id
            WHERE pm.is_active = TRUE
            ORDER BY pm.sort_order
        """).setParameter("tenantId", tenantId).getResultList();

        return rows.stream().map(row -> new ModuleRow(
            (UUID) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4],
            (UUID) row[5], (String) row[6], toOffsetDateTime(row[7]), Boolean.TRUE.equals(row[8]),
            (String) row[9], (String) row[10], (UUID) row[11], ((Number) row[12]).longValue(),
            ((Number) row[13]).intValue() == 1, row[14] != null ? ((Number) row[14]).intValue() : null
        )).toList();
    }

    public record ServiceRow(
        UUID serviceId, String serviceName, String serviceSlug, String serviceDescription, String serviceIconPath,
        UUID serviceGroupId, String serviceGroupName, String routeKey
    ) {}

    @SuppressWarnings("unchecked")
    public List<ServiceRow> listActiveServicesOrdered(UUID moduleId) {
        List<Object[]> rows = em.createNativeQuery("""
            SELECT
              s.id, s.name, s.slug, s.description, s.icon_path,
              CASE WHEN g.status = 'ACTIVE' THEN g.id ELSE NULL END,
              CASE WHEN g.status = 'ACTIVE' THEN g.name ELSE NULL END,
              s.route_key,
              CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE 9999 END
            FROM platform_module_services s
            LEFT JOIN platform_module_service_groups g
              ON g.id = s.service_group_id AND g.status = 'ACTIVE'
            WHERE s.module_id = :moduleId AND s.is_active = TRUE
            ORDER BY
              CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE 9999 END,
              s.sort_order
        """).setParameter("moduleId", moduleId).getResultList();

        return rows.stream().map(row -> new ServiceRow(
            (UUID) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4],
            (UUID) row[5], (String) row[6], (String) row[7]
        )).toList();
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime odt) return odt;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("Tipo de data inesperado: " + value.getClass());
    }
}
