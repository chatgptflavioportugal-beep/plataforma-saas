package com.saas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dono de toda a regra de negócio de Trial Campaign — o "Subscription Service"
 * do sistema de Trial. Único ponto que seleciona a campanha vigente de um
 * módulo/plano, valida cooldown de reutilização e reserva vagas de forma
 * segura contra corrida. Todos os outros pontos do backend (catálogo público,
 * contratação, dashboard) devem passar por aqui em vez de reimplementar a
 * lógica de seleção.
 */
@ApplicationScoped
public class TrialCampaignService {

    private static final String BLOCKED_MESSAGE =
        "Você já utilizou o Trial deste módulo recentemente. Um novo Trial poderá ser " +
        "iniciado após o período mínimo definido pela plataforma.";

    private static final String CANCELLED_MESSAGE = "Este período promocional foi encerrado.";

    private static final String NO_VACANCY_MESSAGE = "Não há vagas disponíveis para Trial deste módulo no momento.";

    @Inject
    EntityManager em;

    public record CatalogOffer(
        boolean available,
        UUID campaignId,
        String campaignName,
        Integer days
    ) {
        static final CatalogOffer NONE = new CatalogOffer(false, null, null, null);
    }

    public record TrialEligibility(
        boolean eligible,
        UUID campaignId,
        String campaignName,
        Integer days,
        String reasonCode, // NONE | COOLDOWN | NO_CAMPAIGN | CANCELLED
        String cooldownEndsAt // ISO timestamp, texto (apenas quando reasonCode = COOLDOWN)
    ) {}

    public record ModuleTrialStatus(
        boolean eligible,
        String campaignName,
        Integer days,
        boolean hadCampaignEver
    ) {}

    // ─── Seleção da campanha vigente (sem considerar tenant) ─────────────────────

    /**
     * Campanha elegível para um plan_version_module: ACTIVE, com vagas, dentro da
     * janela de datas, maior prioridade (desempate pela mais antiga).
     */
    @SuppressWarnings("unchecked")
    public CatalogOffer resolveCatalogOffer(UUID planVersionModuleId) {
        var rows = em.createNativeQuery(
            "SELECT id, name, days FROM trial_campaigns " +
            "WHERE plan_version_module_id = :pvmId AND status = 'ACTIVE' " +
            "  AND used_slots < max_slots " +
            "  AND (start_date IS NULL OR start_date <= CURRENT_DATE) " +
            "  AND (end_date IS NULL OR end_date >= CURRENT_DATE) " +
            "ORDER BY priority DESC, created_at ASC LIMIT 1"
        ).setParameter("pvmId", planVersionModuleId).getResultList();

        if (rows.isEmpty()) return CatalogOffer.NONE;

        Object[] row = (Object[]) rows.get(0);
        return new CatalogOffer(
            true,
            (UUID) row[0],
            (String) row[1],
            ((Number) row[2]).intValue()
        );
    }

    // ─── Elegibilidade por tenant (cooldown + seleção de campanha) ───────────────

    public TrialEligibility checkEligibility(UUID tenantId, UUID moduleId, UUID planVersionModuleId) {
        String cooldownEndsAt = cooldownEndsAtIfBlocked(tenantId, moduleId);
        if (cooldownEndsAt != null) {
            return new TrialEligibility(false, null, null, null, "COOLDOWN", cooldownEndsAt);
        }

        CatalogOffer offer = resolveCatalogOffer(planVersionModuleId);
        if (!offer.available()) {
            String reasonCode = hasCancelledCampaign(planVersionModuleId) ? "CANCELLED" : "NO_CAMPAIGN";
            return new TrialEligibility(false, null, null, null, reasonCode, null);
        }

        return new TrialEligibility(true, offer.campaignId(), offer.campaignName(), offer.days(), "NONE", null);
    }

    /**
     * Existe uma campanha CANCELLED (mais recente) para este plan_version_module?
     * Usado só para dar uma mensagem mais específica ("período encerrado") em vez
     * do genérico "sem vagas" quando o motivo real é cancelamento administrativo.
     */
    private boolean hasCancelledCampaign(UUID planVersionModuleId) {
        long count = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM trial_campaigns " +
            "WHERE plan_version_module_id = :pvmId AND status = 'CANCELLED'"
        ).setParameter("pvmId", planVersionModuleId).getSingleResult()).longValue();
        return count > 0;
    }

    /**
     * Se o tenant ainda está dentro do período de cooldown de reutilização deste
     * módulo (com base no último Trial concluído, module_trial_history.trial_finished_at),
     * retorna a data (texto ISO) em que o cooldown termina. Retorna null se o tenant
     * nunca usou Trial deste módulo, se o Trial mais recente ainda não terminou
     * (trial_finished_at nulo — em andamento) ou se o cooldown já passou.
     * Toda a aritmética de datas é feita em SQL para evitar ambiguidade de tipo do
     * driver JDBC com TIMESTAMPTZ.
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

    // ─── Reserva de vaga (transacional, segura contra corrida) ───────────────────

    /**
     * Reivindica uma vaga na campanha elegível para tenant+módulo, com retry em
     * caso de perda de corrida contra outra requisição concorrente. Lança
     * BadRequestException com a mensagem correta (cooldown ou sem vagas) se não
     * for possível conceder o Trial.
     */
    @Transactional
    public UUID claimSlotOrThrow(UUID tenantId, UUID moduleId, UUID planVersionModuleId) {
        if (cooldownEndsAtIfBlocked(tenantId, moduleId) != null) {
            throw new BadRequestException(BLOCKED_MESSAGE);
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            CatalogOffer offer = resolveCatalogOffer(planVersionModuleId);
            if (!offer.available()) {
                throw new BadRequestException(
                    hasCancelledCampaign(planVersionModuleId) ? CANCELLED_MESSAGE : NO_VACANCY_MESSAGE);
            }

            int updated = em.createNativeQuery(
                "UPDATE trial_campaigns SET used_slots = used_slots + 1, updated_at = NOW() " +
                "WHERE id = :id AND used_slots < max_slots"
            ).setParameter("id", offer.campaignId()).executeUpdate();

            if (updated == 1) return offer.campaignId();
            // outra requisição venceu a corrida nesta campanha — tenta de novo
        }

        throw new BadRequestException(NO_VACANCY_MESSAGE);
    }

    // ─── Cancelamento em massa (nova versão do plano) ────────────────────────────

    /**
     * Cancela todas as campanhas ACTIVE/SCHEDULED vinculadas a qualquer módulo da
     * versão de plano informada — usado quando uma nova versão do plano é criada, já
     * que a campanha promovia especificamente aquela versão antiga. Não mexe em
     * used_slots, module_trial_history nem profile_module_subscriptions: participantes
     * que já entraram continuam normalmente, só novas adesões deixam de ser possíveis.
     * Retorna os ids cancelados para o chamador registrar auditoria.
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public List<UUID> cancelCampaignsForPlanVersion(String oldPlanId, String reason, UUID actorUserId) {
        List<String> ids = em.createNativeQuery(
            "SELECT tc.id::text FROM trial_campaigns tc " +
            "JOIN plan_version_modules pvm ON pvm.id = tc.plan_version_module_id " +
            "WHERE pvm.plan_id::text = :oldPlanId AND tc.status IN ('ACTIVE', 'SCHEDULED')"
        ).setParameter("oldPlanId", oldPlanId).getResultList();

        if (ids.isEmpty()) return List.of();

        em.createNativeQuery(
            "UPDATE trial_campaigns SET status = 'CANCELLED', cancelled_at = NOW(), cancel_reason = :reason, " +
            "updated_at = NOW(), updated_by_user_id = :actorUserId " +
            "WHERE id::text IN (:ids)"
        )
            .setParameter("reason", reason)
            .setParameter("actorUserId", actorUserId)
            .setParameter("ids", ids)
            .executeUpdate();

        return ids.stream().map(UUID::fromString).collect(Collectors.toList());
    }

    // ─── Configuração global ──────────────────────────────────────────────────────

    public int cooldownDays() {
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
