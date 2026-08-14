package com.saas.subscription.repository;

import com.saas.subscription.entity.ProfileModuleSubscription;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProfileModuleSubscriptionRepository implements PanacheRepositoryBase<ProfileModuleSubscription, UUID> {

    @Inject
    EntityManager em;

    public Optional<ProfileModuleSubscription> findByTenantAndModule(UUID tenantId, UUID moduleId) {
        return find("tenantId = ?1 and moduleId = ?2", tenantId, moduleId).firstResultOptional();
    }

    public Optional<ProfileModuleSubscription> findByIdAndTenantAndStatusIn(UUID id, UUID tenantId, List<String> statuses) {
        return find("id = ?1 and tenantId = ?2 and status in ?3", id, tenantId, statuses).firstResultOptional();
    }

    public Optional<ProfileModuleSubscription> findByIdAndTenantWithFutureOrNullExpiry(UUID id, UUID tenantId, List<String> statuses) {
        return find("id = ?1 and tenantId = ?2 and status in ?3 and (expiresAt is null or expiresAt > current_timestamp)",
            id, tenantId, statuses).firstResultOptional();
    }

    public boolean hasActiveUnexpired(UUID tenantId, UUID moduleId) {
        return count("tenantId = ?1 and moduleId = ?2 and status = 'ACTIVE' and (expiresAt is null or expiresAt > current_timestamp)",
            tenantId, moduleId) > 0;
    }

    /**
     * Upsert nativo (ON CONFLICT DO UPDATE) — mantido como Native Query
     * deliberadamente: a chave lógica (tenant_id, module_id) precisa de
     * atomicidade contra duas requisições concorrentes contratando o mesmo
     * módulo para o mesmo perfil; um "find então persist/merge" via ORM
     * reintroduziria essa condição de corrida (TOCTOU). Ver diagnóstico,
     * seção "Banco de dados".
     */
    public void upsertContractedModule(UUID tenantId, UUID moduleId, UUID planVersionId, String billingCycle,
                                        String status, OffsetDateTime expiresAt, UUID userId, Integer trialDays,
                                        OffsetDateTime trialStartAt, OffsetDateTime trialEndAt,
                                        OffsetDateTime billingStartsAt, UUID trialCampaignId, UUID trialHistoryId) {
        em.createNativeQuery("""
            INSERT INTO profile_module_subscriptions
                (tenant_id, module_id, plan_version_id, billing_cycle, status,
                 started_at, expires_at, canceled_at, created_by_user_id,
                 trial_days, trial_start_at, trial_end_at, billing_starts_at,
                 trial_campaign_id, trial_history_id)
            VALUES
                (:tenantId, :moduleId, :planVersionId, :billingCycle, :status,
                 NOW(), :expiresAt, NULL, :userId,
                 :trialDays, :trialStartAt, :trialEndAt, :billingStartsAt,
                 :trialCampaignId, :trialHistoryId)
            ON CONFLICT (tenant_id, module_id)
            DO UPDATE SET
                plan_version_id    = EXCLUDED.plan_version_id,
                billing_cycle      = EXCLUDED.billing_cycle,
                status             = EXCLUDED.status,
                started_at         = NOW(),
                expires_at         = EXCLUDED.expires_at,
                canceled_at        = NULL,
                trial_days         = EXCLUDED.trial_days,
                trial_start_at     = EXCLUDED.trial_start_at,
                trial_end_at       = EXCLUDED.trial_end_at,
                billing_starts_at  = EXCLUDED.billing_starts_at,
                trial_campaign_id  = EXCLUDED.trial_campaign_id,
                trial_history_id   = EXCLUDED.trial_history_id,
                updated_at         = NOW()
        """)
            .setParameter("tenantId", tenantId)
            .setParameter("moduleId", moduleId)
            .setParameter("planVersionId", planVersionId)
            .setParameter("billingCycle", billingCycle)
            .setParameter("status", status)
            .setParameter("expiresAt", expiresAt)
            .setParameter("userId", userId)
            .setParameter("trialDays", trialDays)
            .setParameter("trialStartAt", trialStartAt)
            .setParameter("trialEndAt", trialEndAt)
            .setParameter("billingStartsAt", billingStartsAt)
            .setParameter("trialCampaignId", trialCampaignId)
            .setParameter("trialHistoryId", trialHistoryId)
            .executeUpdate();
    }

    public void updateBillingCyclePreference(UUID tenantId, UUID moduleId, String billingCycle) {
        update("billingCycle = ?1 where tenantId = ?2 and moduleId = ?3", billingCycle, tenantId, moduleId);
    }

    public void upsertFreeActivation(UUID tenantId, UUID moduleId, UUID planVersionId, UUID userId) {
        em.createNativeQuery("""
            INSERT INTO profile_module_subscriptions
                (tenant_id, module_id, plan_version_id, billing_cycle, status,
                 started_at, expires_at, canceled_at, created_by_user_id)
            VALUES
                (:tenantId, :moduleId, :planVersionId, 'FREE', 'ACTIVE',
                 NOW(), NULL, NULL, :userId)
            ON CONFLICT (tenant_id, module_id)
            DO UPDATE SET
                plan_version_id    = EXCLUDED.plan_version_id,
                billing_cycle      = 'FREE',
                status             = 'ACTIVE',
                started_at         = NOW(),
                expires_at         = NULL,
                canceled_at        = NULL,
                updated_at         = NOW()
        """)
            .setParameter("tenantId", tenantId)
            .setParameter("moduleId", moduleId)
            .setParameter("planVersionId", planVersionId)
            .setParameter("userId", userId)
            .executeUpdate();
    }

    public record SubscriptionListRow(
        UUID id, UUID tenantId, String tenantType, UUID moduleId, String moduleName, String moduleIconPath,
        UUID planVersionId, UUID planId, String planName, String planCode, Integer planVersion, Integer planSortOrder,
        String billingCycle, BigDecimal monthlyPrice, BigDecimal annualMonthlyPrice, String status,
        OffsetDateTime startedAt, OffsetDateTime expiresAt, OffsetDateTime canceledAt, Integer trialDays,
        OffsetDateTime trialStartAt, OffsetDateTime trialEndAt, OffsetDateTime billingStartsAt,
        UUID trialCampaignId, String limitsJson
    ) {}

    /**
     * Consulta de leitura agregada com JOINs em 4 tabelas e um sub-SELECT
     * json_agg (limites do plano) — mantida como Native Query (benefício real
     * de performance: uma única ida ao banco em vez de N+1 entidades JPA
     * carregadas e agregadas em memória). Object[] nunca sai desta classe —
     * cada linha é convertida para SubscriptionListRow aqui mesmo.
     */
    @SuppressWarnings("unchecked")
    public List<SubscriptionListRow> listByTenantOrderByStartedDesc(UUID tenantId) {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT " +
            "  pms.id, pms.tenant_id, t.type AS profile_type, pms.module_id, pm.name AS module_name, " +
            "  pm.icon_path AS module_icon_path, pms.plan_version_id, p.id AS plan_id, p.name AS plan_name, " +
            "  p.code AS plan_code, p.version AS plan_version, p.sort_order AS plan_sort_order, pms.billing_cycle, " +
            "  pvm.monthly_price, pvm.annual_monthly_price, pms.status, pms.started_at, pms.expires_at, " +
            "  pms.canceled_at, pms.trial_days, pms.trial_start_at, pms.trial_end_at, pms.billing_starts_at, " +
            "  pms.trial_campaign_id, " +
            "  COALESCE((SELECT json_agg(json_build_object(" +
            "    'title', pvml.title, 'description', pvml.description," +
            "    'limit_value', pvml.limit_value, 'unit', pvml.unit, 'sort_order', pvml.sort_order" +
            "  ) ORDER BY pvml.sort_order) FROM plan_version_module_limits pvml" +
            "  WHERE pvml.plan_version_module_id = pvm.id), '[]'::json)::text AS limits_json " +
            "FROM profile_module_subscriptions pms " +
            "JOIN platform_modules pm ON pm.id = pms.module_id " +
            "JOIN plan_version_modules pvm ON pvm.id = pms.plan_version_id " +
            "JOIN plans p ON p.id = pvm.plan_id " +
            "JOIN tenants t ON t.id = pms.tenant_id " +
            "WHERE pms.tenant_id = :tenantId " +
            "ORDER BY pms.started_at DESC"
        ).setParameter("tenantId", tenantId).getResultList();

        return rows.stream().map(row -> new SubscriptionListRow(
            (UUID) row[0], (UUID) row[1], (String) row[2], (UUID) row[3], (String) row[4], (String) row[5],
            (UUID) row[6], (UUID) row[7], (String) row[8], (String) row[9],
            row[10] != null ? ((Number) row[10]).intValue() : null,
            row[11] != null ? ((Number) row[11]).intValue() : null,
            (String) row[12], (BigDecimal) row[13], (BigDecimal) row[14], (String) row[15],
            toOffsetDateTime(row[16]), toOffsetDateTime(row[17]), toOffsetDateTime(row[18]),
            row[19] != null ? ((Number) row[19]).intValue() : null,
            toOffsetDateTime(row[20]), toOffsetDateTime(row[21]), toOffsetDateTime(row[22]),
            (UUID) row[23], (String) row[24]
        )).toList();
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime odt) return odt;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("Tipo de data inesperado: " + value.getClass());
    }
}
