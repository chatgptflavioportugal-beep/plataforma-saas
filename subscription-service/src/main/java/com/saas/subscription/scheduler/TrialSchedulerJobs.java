package com.saas.subscription.scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Jobs de housekeeping do sistema de Trial: mantém profile_module_subscriptions.status
 * coerente com expires_at e mantém trial_campaigns.status coerente com sua janela de
 * datas (start_date/end_date).
 *
 * As checagens de acesso em tempo real (module-catalog-service, auth-service,
 * TenantSubscriptionRepository) já tratam uma assinatura ACTIVE, TRIAL ou
 * TRIAL_CANCELLED vencida como expirada independente deste job — este scheduler só
 * mantém a coluna status correta para telas que exibem o valor persistido (ex.:
 * badge de status em SubscriptionsPage). Da mesma forma, a disponibilidade de vagas
 * de uma trial_campaign é sempre calculada ao vivo (used_slots < max_slots) nas
 * queries de seleção — esgotar as vagas não muda o status armazenado, só as
 * transições de data (SCHEDULED→ACTIVE, ACTIVE→CLOSED).
 */
@ApplicationScoped
public class TrialSchedulerJobs {

    private static final Logger LOG = Logger.getLogger(TrialSchedulerJobs.class);

    @Inject
    EntityManager em;

    /**
     * Dois caminhos distintos ao vencer trial_end_at (expires_at):
     *   TRIAL           → PENDING_PAYMENT (o usuário não cancelou; simula tentativa de
     *                      cobrança — no futuro isso dispara a cobrança real, sem mudar
     *                      a arquitetura)
     *   TRIAL_CANCELLED → EXPIRED (o usuário já tinha cancelado a renovação; nunca cobra)
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    @SuppressWarnings("unchecked")
    public void expireOverdueModuleSubscriptions() {
        List<UUID> affectedTenantIds = em.createNativeQuery(
                "SELECT DISTINCT tenant_id FROM profile_module_subscriptions " +
                "WHERE status IN ('ACTIVE', 'TRIAL', 'TRIAL_CANCELLED') " +
                "  AND expires_at IS NOT NULL AND expires_at < NOW()"
        ).getResultList();

        if (affectedTenantIds.isEmpty()) return;

        // Fecha o histórico de Trial (trial_finished_at) para os módulos cujo Trial —
        // cancelado ou não — está efetivamente terminando agora. Usa trial_history_id
        // (não tenant_id+module_id) para não ambiguar com histórico antigo do mesmo
        // tenant+módulo, já que module_trial_history agora é um ledger append-only.
        em.createNativeQuery(
                "UPDATE module_trial_history h SET trial_finished_at = NOW() " +
                "FROM profile_module_subscriptions pms " +
                "WHERE h.id = pms.trial_history_id " +
                "  AND h.trial_finished_at IS NULL " +
                "  AND pms.status IN ('TRIAL', 'TRIAL_CANCELLED') " +
                "  AND pms.expires_at IS NOT NULL AND pms.expires_at < NOW()"
        ).executeUpdate();

        // TRIAL_CANCELLED: o usuário já tinha optado por não renovar — nunca cobra,
        // vai direto para EXPIRED e bloqueia o módulo.
        int expiredCancelled = em.createNativeQuery(
                "UPDATE profile_module_subscriptions SET status = 'EXPIRED', updated_at = NOW() " +
                "WHERE status IN ('ACTIVE', 'TRIAL_CANCELLED') AND expires_at IS NOT NULL AND expires_at < NOW()"
        ).executeUpdate();

        // TRIAL (não cancelado): o usuário não desistiu — simula a tentativa de
        // cobrança automática (PENDING_PAYMENT), preparado para a cobrança real futura.
        int pendingPayment = em.createNativeQuery(
                "UPDATE profile_module_subscriptions SET status = 'PENDING_PAYMENT', updated_at = NOW() " +
                "WHERE status = 'TRIAL' AND expires_at IS NOT NULL AND expires_at < NOW()"
        ).executeUpdate();

        // Assinatura de módulo expirou — invalida PAT/MAT em cache dos membros do tenant
        // para que o front reavalie o acesso na próxima requisição, sem esperar o MAT expirar.
        em.createNativeQuery(
                "UPDATE user_tenants SET permissions_version = permissions_version + 1 " +
                "WHERE tenant_id IN (:ids) AND is_active = TRUE"
        ).setParameter("ids", affectedTenantIds).executeUpdate();

        LOG.infof("Expiradas %d assinaturas de módulo vencidas (%d aguardando pagamento)", expiredCancelled, pendingPayment);
    }

    /**
     * Mantém trial_campaigns.status coerente com sua janela de datas:
     *   SCHEDULED → ACTIVE quando start_date chega
     *   ACTIVE    → CLOSED quando end_date passa
     * Esgotamento de vagas não é tratado aqui — é sempre calculado ao vivo.
     */
    @Scheduled(cron = "0 15 * * * ?")
    @Transactional
    public void syncTrialCampaignStatuses() {
        int scheduledToActive = em.createNativeQuery(
                "UPDATE trial_campaigns SET status = 'ACTIVE', updated_at = NOW() " +
                "WHERE status = 'SCHEDULED' AND (start_date IS NULL OR start_date <= CURRENT_DATE)"
        ).executeUpdate();

        int activeToClosed = em.createNativeQuery(
                "UPDATE trial_campaigns SET status = 'CLOSED', updated_at = NOW() " +
                "WHERE status = 'ACTIVE' AND end_date IS NOT NULL AND end_date < CURRENT_DATE"
        ).executeUpdate();

        if (scheduledToActive > 0 || activeToClosed > 0) {
            LOG.infof("Trial campaigns: %d SCHEDULED→ACTIVE, %d ACTIVE→CLOSED", scheduledToActive, activeToClosed);
        }
    }
}
