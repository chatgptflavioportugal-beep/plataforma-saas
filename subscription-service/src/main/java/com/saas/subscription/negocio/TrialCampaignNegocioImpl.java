package com.saas.subscription.negocio;

import com.saas.subscription.dao.ModuleTrialHistoryDAO;
import com.saas.subscription.dao.PlatformSettingDAO;
import com.saas.subscription.dao.TrialCampaignDAO;
import com.saas.subscription.entity.TrialCampaign;
import com.saas.subscription.negocio.impl.TrialCampaignNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dono de toda a regra de negócio de Trial Campaign — o "Subscription Service"
 * do sistema de Trial. Único ponto que seleciona a campanha vigente de um
 * módulo/plano, valida cooldown de reutilização e reserva vagas de forma
 * segura contra corrida. Todos os outros pontos do backend (catálogo público,
 * contratação, dashboard) devem passar por aqui em vez de reimplementar a
 * lógica de seleção.
 */
@ApplicationScoped
public class TrialCampaignNegocioImpl implements TrialCampaignNegocio {

    private static final String BLOCKED_MESSAGE =
        "Você já utilizou o Trial deste módulo recentemente. Um novo Trial poderá ser " +
        "iniciado após o período mínimo definido pela plataforma.";

    private static final String CANCELLED_MESSAGE = "Este período promocional foi encerrado.";

    private static final String NO_VACANCY_MESSAGE = "Não há vagas disponíveis para Trial deste módulo no momento.";

    private static final int DEFAULT_COOLDOWN_DAYS = 365;

    @Inject
    TrialCampaignDAO trialCampaignDAO;

    @Inject
    ModuleTrialHistoryDAO moduleTrialHistoryDAO;

    @Inject
    PlatformSettingDAO platformSettingDAO;

    // ─── Seleção da campanha vigente (sem considerar tenant) ─────────────────────

    @Override
    public CatalogOffer resolveCatalogOffer(UUID planVersionModuleId) {
        return trialCampaignDAO.findSelectableForPlanVersionModule(planVersionModuleId)
            .map(tc -> new CatalogOffer(true, tc.id, tc.name, tc.days))
            .orElse(CatalogOffer.NONE);
    }

    // ─── Elegibilidade por tenant (cooldown + seleção de campanha) ───────────────

    @Override
    public TrialEligibility checkEligibility(UUID tenantId, UUID moduleId, UUID planVersionModuleId) {
        OffsetDateTime cooldownEndsAt = cooldownEndsAtIfBlocked(tenantId, moduleId);
        if (cooldownEndsAt != null) {
            return new TrialEligibility(false, null, null, null, "COOLDOWN", cooldownEndsAt.toString());
        }

        CatalogOffer offer = resolveCatalogOffer(planVersionModuleId);
        if (!offer.available()) {
            String reasonCode = trialCampaignDAO.hasCancelledCampaign(planVersionModuleId) ? "CANCELLED" : "NO_CAMPAIGN";
            return new TrialEligibility(false, null, null, null, reasonCode, null);
        }

        return new TrialEligibility(true, offer.campaignId(), offer.campaignName(), offer.days(), "NONE", null);
    }

    private OffsetDateTime cooldownEndsAtIfBlocked(UUID tenantId, UUID moduleId) {
        return moduleTrialHistoryDAO.findCooldownEndsAt(tenantId, moduleId, cooldownDays()).orElse(null);
    }

    /**
     * Status de Trial de um módulo inteiro (todas as versões/planos correntes),
     * usado pelo Dashboard para módulos LOCKED (sem assinatura nenhuma ainda) —
     * não há um plan_version_module fixo para consultar.
     */
    @Override
    public ModuleTrialStatus resolveModuleTrialStatus(UUID tenantId, UUID moduleId) {
        if (cooldownEndsAtIfBlocked(tenantId, moduleId) != null) {
            return new ModuleTrialStatus(false, null, null, true);
        }

        var campaigns = trialCampaignDAO.listSelectableForModule(moduleId);
        if (!campaigns.isEmpty()) {
            TrialCampaign tc = campaigns.get(0);
            return new ModuleTrialStatus(true, tc.name, tc.days, true);
        }

        boolean everHadCampaign = trialCampaignDAO.everHadCampaignForModule(moduleId);
        return new ModuleTrialStatus(false, null, null, everHadCampaign);
    }

    // ─── Reserva de vaga (transacional, segura contra corrida) ───────────────────

    /**
     * Reivindica uma vaga na campanha elegível para tenant+módulo, com retry em
     * caso de perda de corrida contra outra requisição concorrente. Lança
     * BadRequestException com a mensagem correta (cooldown ou sem vagas) se não
     * for possível conceder o Trial.
     */
    @Override
    @Transactional
    public UUID claimSlotOrThrow(UUID tenantId, UUID moduleId, UUID planVersionModuleId) {
        if (cooldownEndsAtIfBlocked(tenantId, moduleId) != null) {
            throw new BadRequestException(BLOCKED_MESSAGE);
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            CatalogOffer offer = resolveCatalogOffer(planVersionModuleId);
            if (!offer.available()) {
                throw new BadRequestException(
                    trialCampaignDAO.hasCancelledCampaign(planVersionModuleId) ? CANCELLED_MESSAGE : NO_VACANCY_MESSAGE);
            }

            if (trialCampaignDAO.tryClaimSlot(offer.campaignId())) {
                return offer.campaignId();
            }
            // outra requisição venceu a corrida nesta campanha — tenta de novo
        }

        throw new BadRequestException(NO_VACANCY_MESSAGE);
    }

    // Regra de elegibilidade do plano (Free não pode ter Trial) e cancelamento em
    // massa ao gerar nova versão de plano agora vivem em
    // admin-service.TrialCampaignAdminService — são administração do catálogo de
    // campanhas (trial_campaigns), não elegibilidade/reivindicação por tenant.

    // ─── Configuração global ──────────────────────────────────────────────────────

    /**
     * Dias mínimos de cooldown entre Trials do mesmo módulo. Se a configuração
     * platform_settings.trial_reuse_cooldown_days estiver ausente ou em formato
     * inválido, cai no default — mas só nesses dois casos específicos, não para
     * qualquer falha (ex.: erro de conexão com o banco não deve ser mascarado
     * silenciosamente).
     */
    @Override
    public int cooldownDays() {
        var value = platformSettingDAO.findValue("trial_reuse_cooldown_days");
        if (value.isEmpty()) return DEFAULT_COOLDOWN_DAYS;
        try {
            return Integer.parseInt(value.get());
        } catch (NumberFormatException e) {
            return DEFAULT_COOLDOWN_DAYS;
        }
    }
}
