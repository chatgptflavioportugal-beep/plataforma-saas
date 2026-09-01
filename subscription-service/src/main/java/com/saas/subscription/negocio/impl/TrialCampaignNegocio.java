package com.saas.subscription.negocio.impl;

import java.util.UUID;

/**
 * Dono de toda a regra de negócio de Trial Campaign — o "Subscription Service"
 * do sistema de Trial. Único ponto que seleciona a campanha vigente de um
 * módulo/plano, valida cooldown de reutilização e reserva vagas de forma
 * segura contra corrida. Todos os outros pontos do backend (catálogo público,
 * contratação, dashboard) devem passar por aqui em vez de reimplementar a
 * lógica de seleção.
 */
public interface TrialCampaignNegocio {

    record CatalogOffer(
        boolean available,
        UUID campaignId,
        String campaignName,
        Integer days
    ) {
        public static final CatalogOffer NONE = new CatalogOffer(false, null, null, null);
    }

    record TrialEligibility(
        boolean eligible,
        UUID campaignId,
        String campaignName,
        Integer days,
        String reasonCode, // NONE | COOLDOWN | NO_CAMPAIGN | CANCELLED
        String cooldownEndsAt // ISO timestamp, texto (apenas quando reasonCode = COOLDOWN)
    ) {}

    record ModuleTrialStatus(
        boolean eligible,
        String campaignName,
        Integer days,
        boolean hadCampaignEver
    ) {}

    CatalogOffer resolveCatalogOffer(UUID planVersionModuleId);

    TrialEligibility checkEligibility(UUID tenantId, UUID moduleId, UUID planVersionModuleId);

    ModuleTrialStatus resolveModuleTrialStatus(UUID tenantId, UUID moduleId);

    UUID claimSlotOrThrow(UUID tenantId, UUID moduleId, UUID planVersionModuleId);

    int cooldownDays();
}
