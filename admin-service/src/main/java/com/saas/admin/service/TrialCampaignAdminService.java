package com.saas.admin.service;

import com.saas.admin.dao.TrialCampaignDAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Regras administrativas sobre Trial Campaigns que precisam ser locais a
 * admin-service (mesma unidade transacional de AdminPlanService/
 * AdminTrialCampaignResource, donos de trial_campaigns). A elegibilidade e a
 * reserva de vaga por tenant (checkEligibility/claimSlotOrThrow/
 * resolveCatalogOffer/resolveModuleTrialStatus) continuam em
 * subscription-service — são consultas/reivindicações feitas pelo próprio
 * cliente, não administração do catálogo de campanhas.
 */
@ApplicationScoped
public class TrialCampaignAdminService {

    @Inject
    TrialCampaignDAO dao;

    /**
     * O plano Free já é gratuito permanentemente, então não faz sentido oferecer
     * Trial para ele. "Free" aqui é o plano com code = 'free' — não o preço do
     * módulo, já que um mesmo módulo pode ter preço zero dentro de um plano pago
     * sem que o plano em si seja o Free.
     */
    public boolean isFreePlanVersionModule(String planVersionModuleId) {
        return dao.isFreePlanVersionModule(planVersionModuleId);
    }

    /**
     * Cancela todas as campanhas ACTIVE/SCHEDULED vinculadas a qualquer módulo da
     * versão de plano informada — usado quando uma nova versão do plano é criada,
     * já que a campanha promovia especificamente aquela versão antiga. Não mexe em
     * used_slots, module_trial_history nem profile_module_subscriptions:
     * participantes que já entraram continuam normalmente, só novas adesões
     * deixam de ser possíveis. Retorna os ids cancelados para o chamador
     * registrar auditoria.
     */
    @Transactional
    public List<UUID> cancelCampaignsForPlanVersion(String oldPlanId, String reason, UUID actorUserId) {
        List<String> ids = dao.findActiveOrScheduledCampaignIds(oldPlanId);
        if (ids.isEmpty()) return List.of();

        dao.bulkCancel(ids, reason, actorUserId);

        return ids.stream().map(UUID::fromString).collect(Collectors.toList());
    }
}
