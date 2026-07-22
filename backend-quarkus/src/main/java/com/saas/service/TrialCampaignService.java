package com.saas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.UUID;

/**
 * Cópia somente-leitura usada por DashboardResource para saber se um módulo LOCKED
 * (sem assinatura nenhuma ainda) tem Trial disponível. O ciclo de vida completo de
 * Trial Campaign (seleção, elegibilidade, reserva de vaga, CRUD administrativo) foi
 * movido para o subscription-service (Fase 4) — esta classe existe apenas para não
 * fazer round-trip de rede num dado exibido em toda carga do dashboard.
 */
@ApplicationScoped
public class TrialCampaignService {

    @Inject
    EntityManager em;

    public record ModuleTrialStatus(
        boolean eligible,
        String campaignName,
        Integer days,
        boolean hadCampaignEver
    ) {}

    /**
     * Status de Trial de um módulo inteiro (todas as versões/planos correntes),
     * usado pelo Dashboard para módulos LOCKED (sem assinatura nenhuma ainda) —
     * não há um plan_version_module fixo para consultar.
     */
    @SuppressWarnings("unchecked")
    public ModuleTrialStatus resolveModuleTrialStatus(UUID tenantId, UUID moduleId) {
        if (cooldownEndsAtIfBlocked(tenantId, moduleId) != null) {
            return new ModuleTrialStatus(false, null, null, true);
        }

        var rows = em.createNativeQuery(
            "SELECT tc.id, tc.name, tc.days FROM trial_campaigns tc " +
            "JOIN plan_version_modules pvm ON pvm.id = tc.plan_version_module_id " +
            "JOIN plans p ON p.id = pvm.plan_id " +
            "WHERE pvm.module_id = :moduleId AND pvm.status = 'active' " +
            "  AND p.is_active = TRUE AND p.is_current_version = TRUE " +
            "  AND tc.status = 'ACTIVE' AND tc.used_slots < tc.max_slots " +
            "  AND (tc.start_date IS NULL OR tc.start_date <= CURRENT_DATE) " +
            "  AND (tc.end_date IS NULL OR tc.end_date >= CURRENT_DATE) " +
            "ORDER BY tc.priority DESC, tc.created_at ASC LIMIT 1"
        ).setParameter("moduleId", moduleId).getResultList();

        if (!rows.isEmpty()) {
            Object[] row = (Object[]) rows.get(0);
            return new ModuleTrialStatus(true, (String) row[1], ((Number) row[2]).intValue(), true);
        }

        long everHadCampaign = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM trial_campaigns tc " +
            "JOIN plan_version_modules pvm ON pvm.id = tc.plan_version_module_id " +
            "WHERE pvm.module_id = :moduleId"
        ).setParameter("moduleId", moduleId).getSingleResult()).longValue();

        return new ModuleTrialStatus(false, null, null, everHadCampaign > 0);
    }

    /**
     * Se o tenant ainda está dentro do período de cooldown de reutilização deste
     * módulo (com base no último Trial concluído, module_trial_history.trial_finished_at),
     * retorna a data (texto ISO) em que o cooldown termina. Retorna null se o tenant
     * nunca usou Trial deste módulo, se o Trial mais recente ainda não terminou
     * (trial_finished_at nulo — em andamento) ou se o cooldown já passou.
     */
    @SuppressWarnings("unchecked")
    private String cooldownEndsAtIfBlocked(UUID tenantId, UUID moduleId) {
        var rows = em.createNativeQuery(
            "SELECT (trial_finished_at + (:cooldownDays || ' days')::interval)::text " +
            "FROM module_trial_history " +
            "WHERE tenant_id = :tenantId AND module_id = :moduleId " +
            "  AND trial_finished_at IS NOT NULL " +
            "  AND trial_finished_at + (:cooldownDays || ' days')::interval > NOW() " +
            "ORDER BY trial_started_at DESC LIMIT 1"
        )
            .setParameter("tenantId", tenantId)
            .setParameter("moduleId", moduleId)
            .setParameter("cooldownDays", cooldownDays())
            .getResultList();

        return rows.isEmpty() ? null : (String) rows.get(0);
    }

    private int cooldownDays() {
        try {
            String value = (String) em.createNativeQuery(
                "SELECT value FROM platform_settings WHERE key = 'trial_reuse_cooldown_days'"
            ).getSingleResult();
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 365;
        }
    }
}
