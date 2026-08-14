package com.saas.subscription.scheduler;

import com.saas.subscription.entity.ProfileModuleSubscription;
import com.saas.subscription.repository.ModuleTrialHistoryRepository;
import com.saas.subscription.repository.ProfileModuleSubscriptionRepository;
import com.saas.subscription.repository.TrialCampaignRepository;
import com.saas.subscription.repository.UserTenantRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Jobs de housekeeping do sistema de Trial: mantém profile_module_subscriptions.status
 * coerente com expires_at e mantém trial_campaigns.status coerente com sua janela de
 * datas (start_date/end_date).
 *
 * As checagens de acesso em tempo real (ModuleAccessService, DashboardService,
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
    ProfileModuleSubscriptionRepository subscriptionRepository;

    @Inject
    ModuleTrialHistoryRepository moduleTrialHistoryRepository;

    @Inject
    UserTenantRepository userTenantRepository;

    @Inject
    TrialCampaignRepository trialCampaignRepository;

    /**
     * Dois caminhos distintos ao vencer trial_end_at (expires_at):
     *   TRIAL           → PENDING_PAYMENT (o usuário não cancelou; simula tentativa de
     *                      cobrança — no futuro isso dispara a cobrança real, sem mudar
     *                      a arquitetura)
     *   TRIAL_CANCELLED → EXPIRED (o usuário já tinha cancelado a renovação; nunca cobra)
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void expireOverdueModuleSubscriptions() {
        List<ProfileModuleSubscription> overdue = subscriptionRepository.list(
            "status in ?1 and expiresAt is not null and expiresAt < ?2",
            List.of("ACTIVE", "TRIAL", "TRIAL_CANCELLED"), OffsetDateTime.now());

        if (overdue.isEmpty()) return;

        List<UUID> affectedTenantIds = overdue.stream().map(s -> s.tenantId).distinct().toList();

        int expiredCancelled = 0;
        int pendingPayment = 0;
        for (ProfileModuleSubscription subscription : overdue) {
            // Fecha o histórico de Trial (trial_finished_at) para os módulos cujo Trial —
            // cancelado ou não — está efetivamente terminando agora.
            if (subscription.trialHistoryId != null
                    && ("TRIAL".equals(subscription.status) || "TRIAL_CANCELLED".equals(subscription.status))) {
                moduleTrialHistoryRepository.markFinished(subscription.trialHistoryId, false);
            }

            if ("TRIAL_CANCELLED".equals(subscription.status) || "ACTIVE".equals(subscription.status)) {
                // O usuário já tinha optado por não renovar (ou nunca teve Trial) — nunca
                // cobra, vai direto para EXPIRED e bloqueia o módulo.
                subscription.status = "EXPIRED";
                expiredCancelled++;
            } else if ("TRIAL".equals(subscription.status)) {
                // TRIAL (não cancelado): o usuário não desistiu — simula a tentativa de
                // cobrança automática (PENDING_PAYMENT), preparado para a cobrança real futura.
                subscription.status = "PENDING_PAYMENT";
                pendingPayment++;
            }
        }

        // Assinatura de módulo expirou — invalida PAT/MAT em cache dos membros do tenant
        // para que o front reavalie o acesso na próxima requisição, sem esperar o MAT expirar.
        userTenantRepository.bumpVersionForTenants(affectedTenantIds);

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
        long scheduledToActive = trialCampaignRepository.update(
            "status = 'ACTIVE' where status = 'SCHEDULED' and (startDate is null or startDate <= current_date)");

        long activeToClosed = trialCampaignRepository.update(
            "status = 'CLOSED' where status = 'ACTIVE' and endDate is not null and endDate < current_date");

        if (scheduledToActive > 0 || activeToClosed > 0) {
            LOG.infof("Trial campaigns: %d SCHEDULED→ACTIVE, %d ACTIVE→CLOSED", scheduledToActive, activeToClosed);
        }
    }
}
