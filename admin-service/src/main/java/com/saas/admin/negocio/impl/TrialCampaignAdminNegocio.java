package com.saas.admin.negocio.impl;

import java.util.List;
import java.util.UUID;

/**
 * Regras administrativas sobre Trial Campaigns que precisam ser locais a
 * admin-service (mesma unidade transacional de AdminPlanNegocio/
 * AdminTrialCampaignResource, donos de trial_campaigns). A elegibilidade e a
 * reserva de vaga por tenant (checkEligibility/claimSlotOrThrow/
 * resolveCatalogOffer/resolveModuleTrialStatus) continuam em
 * subscription-service — são consultas/reivindicações feitas pelo próprio
 * cliente, não administração do catálogo de campanhas.
 */
public interface TrialCampaignAdminNegocio {

    boolean isFreePlanVersionModule(String planVersionModuleId);

    List<UUID> cancelCampaignsForPlanVersion(String oldPlanId, String reason, UUID actorUserId);
}
