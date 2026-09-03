package com.saas.subscription.negocio;

import com.saas.subscription.dao.ModuleTrialHistoryDAO;
import com.saas.subscription.dao.PlanVersionModuleDAO;
import com.saas.subscription.dao.PlatformModuleDAO;
import com.saas.subscription.dao.ProfileAccessLevelAdminPermissionDAO;
import com.saas.subscription.dao.ProfileModuleSubscriptionDAO;
import com.saas.subscription.dao.TenantDAO;
import com.saas.subscription.dao.UserTenantDAO;
import com.saas.subscription.dto.request.ConfirmSubscriptionRequest;
import com.saas.subscription.dto.request.ModuleSubscriptionItem;
import com.saas.subscription.dto.response.ModuleSubscriptionResponse;
import com.saas.subscription.dto.response.TrialEligibilityResponse;
import com.saas.subscription.dto.response.TrialHistoryResponse;
import com.saas.subscription.entity.ModuleTrialHistory;
import com.saas.subscription.entity.PlatformModule;
import com.saas.subscription.entity.ProfileModuleSubscription;
import com.saas.subscription.negocio.impl.ProfileModuleSubscriptionNegocio;
import com.saas.subscription.negocio.impl.TrialCampaignNegocio;
import com.saas.platformtenant.TenantContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Assinaturas de módulo do perfil ativo (tenant), acionadas pelo próprio
 * cliente: contratação, ativação de plano gratuito, listagem, cancelamento,
 * reativação e consulta de Trial. Distinto de AdminSubscriptionNegocioImpl
 * (escopo administrativo sobre qualquer tenant).
 */
@ApplicationScoped
public class ProfileModuleSubscriptionNegocioImpl implements ProfileModuleSubscriptionNegocio {

    private static final List<String> CONTRACT_ROLES = List.of("owner", "admin", "finance");

    @Inject
    ProfileModuleSubscriptionDAO subscriptionDAO;

    @Inject
    PlatformModuleDAO platformModuleDAO;

    @Inject
    PlanVersionModuleDAO planVersionModuleDAO;

    @Inject
    ModuleTrialHistoryDAO moduleTrialHistoryDAO;

    @Inject
    UserTenantDAO userTenantDAO;

    @Inject
    TenantDAO tenantDAO;

    @Inject
    ProfileAccessLevelAdminPermissionDAO profileAccessLevelAdminPermissionDAO;

    @Inject
    TrialCampaignNegocio trialCampaignNegocio;

    // ─── POST /modules — confirmar assinatura ────────────────────────────────────

    @Override
    @Transactional
    public ConfirmResult confirmModuleSubscriptions(ConfirmSubscriptionRequest request, TenantContext ctx) {
        if (!CONTRACT_ROLES.contains(ctx.getUserRole())) {
            throw new ForbiddenException("Sem permissão para contratar módulos neste perfil");
        }
        if (request == null || request.modules() == null || request.modules().isEmpty()) {
            throw new BadRequestException("Nenhum módulo selecionado");
        }

        UUID tenantId = ctx.getTenantId();
        UUID userId = ctx.getUserId();

        for (ModuleSubscriptionItem item : request.modules()) {
            confirmOne(tenantId, userId, item);
        }

        userTenantDAO.bumpVersionForTenant(tenantId);
        return new ConfirmResult(tenantId, request.modules().size());
    }

    private void confirmOne(UUID tenantId, UUID userId, ModuleSubscriptionItem item) {
        if (item.moduleId() == null || item.planVersionId() == null || item.billingCycle() == null) {
            throw new BadRequestException("moduleId, planVersionId e billingCycle são obrigatórios");
        }

        String billingCycle = item.billingCycle().toUpperCase();
        if (!billingCycle.equals("MONTHLY") && !billingCycle.equals("ANNUAL")) {
            throw new BadRequestException("billingCycle inválido: " + item.billingCycle());
        }

        UUID moduleId;
        UUID planVersionId;
        try {
            moduleId = UUID.fromString(item.moduleId());
            planVersionId = UUID.fromString(item.planVersionId());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("ID inválido: " + e.getMessage());
        }

        if (!planVersionModuleDAO.existsActiveForModule(planVersionId, moduleId)) {
            throw new BadRequestException("Plano inválido ou inativo para o módulo " + item.moduleId());
        }

        var existingOpt = subscriptionDAO.findByTenantAndModule(tenantId, moduleId);

        boolean trialOngoing = existingOpt.isPresent()
            && List.of("TRIAL", "TRIAL_CANCELLED").contains(existingOpt.get().status)
            && existingOpt.get().expiresAt != null && existingOpt.get().expiresAt.isAfter(OffsetDateTime.now());
        boolean sameOngoingTrial = trialOngoing && planVersionId.equals(existingOpt.get().planVersionId);

        if (sameOngoingTrial) {
            // Resubmissão do mesmo Trial já em andamento (ex.: usuário confirma de
            // novo a mesma tela) — não reclama outra vaga nem reinicia o histórico,
            // só atualiza a preferência de ciclo de cobrança para depois do Trial.
            subscriptionDAO.updateBillingCyclePreference(tenantId, moduleId, billingCycle);
            return;
        }

        var eligibility = trialCampaignNegocio.checkEligibility(tenantId, moduleId, planVersionId);
        boolean trialEnabled = eligibility.eligible();
        UUID claimedCampaignId = null;
        Integer trialDays = null;

        if (trialEnabled) {
            try {
                claimedCampaignId = trialCampaignNegocio.claimSlotOrThrow(tenantId, moduleId, planVersionId);
                trialDays = eligibility.days();
            } catch (BadRequestException raceLost) {
                // Perdeu a corrida pela última vaga para outra requisição concorrente —
                // segue o fluxo normal de contratação (ACTIVE) em vez de falhar.
                trialEnabled = false;
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String status;
        OffsetDateTime trialStartAt;
        OffsetDateTime trialEndAt;
        OffsetDateTime billingStartsAt;
        OffsetDateTime expiresAt;

        if (trialEnabled) {
            status = "TRIAL";
            trialStartAt = now;
            trialEndAt = now.plusDays(trialDays);
            billingStartsAt = trialEndAt;
            expiresAt = trialEndAt;
        } else {
            status = "ACTIVE";
            trialStartAt = null;
            trialEndAt = null;
            billingStartsAt = null;
            expiresAt = billingCycle.equals("MONTHLY") ? now.plusMonths(1) : now.plusYears(1);
        }

        // Perfil estava em Trial e esta contratação o substitui — ou vira cliente
        // pagante, ou troca para um Trial diferente. Em ambos os casos o Trial
        // anterior está encerrando agora; fecha o registro de histórico
        // correspondente. "Virou cliente" só quando a substituição NÃO é por outro Trial.
        if (existingOpt.isPresent()) {
            ProfileModuleSubscription existing = existingOpt.get();
            boolean wasTrial = "TRIAL".equals(existing.status) || "TRIAL_CANCELLED".equals(existing.status);
            if (wasTrial && existing.trialHistoryId != null) {
                moduleTrialHistoryDAO.markFinished(existing.trialHistoryId, !trialEnabled);
            }
        }

        // Registra o início deste Trial no ledger permanente (histórico de
        // participação, nunca removido) — usado tanto para o cooldown de
        // reutilização quanto para o relatório de Participantes de cada campanha.
        UUID historyId = null;
        if (trialEnabled) {
            historyId = UUID.randomUUID();
            ModuleTrialHistory history = new ModuleTrialHistory();
            history.id = historyId;
            history.tenantId = tenantId;
            history.moduleId = moduleId;
            history.planVersionModuleId = planVersionId;
            history.trialCampaignId = claimedCampaignId;
            history.startedByUserId = userId;
            history.trialStartedAt = trialStartAt;
            moduleTrialHistoryDAO.persist(history);
        }

        // Upsert: atualiza se já existir assinatura para este módulo neste perfil.
        // Trocar de plano durante um Trial cancela o Trial atual e inicia o do novo
        // plano (mesma linha, mesmo comportamento de upsert já usado para preços/limites).
        subscriptionDAO.upsertContractedModule(tenantId, moduleId, planVersionId, billingCycle, status,
            expiresAt, userId, trialDays, trialStartAt, trialEndAt, billingStartsAt, claimedCampaignId, historyId);
    }

    // ─── POST /free — ativação sob demanda de módulo com plano Free ─────────────

    @Override
    @Transactional
    public FreeActivationResult activateFreeModule(String moduleSlug, TenantContext ctx) {
        if (moduleSlug == null || moduleSlug.isBlank()) {
            throw new BadRequestException("moduleSlug é obrigatório");
        }
        if (!canActivateFreeModule(ctx)) {
            throw new ForbiddenException("Sem permissão para ativar módulos gratuitos neste perfil");
        }

        var module = platformModuleBySlug(moduleSlug);

        var freeOpt = planVersionModuleDAO.findFreeForModule(module.id);
        if (freeOpt.isEmpty()) {
            throw new BadRequestException("Este módulo não possui plano gratuito");
        }
        var free = freeOpt.get();

        UUID tenantId = ctx.getTenantId();
        boolean alreadyActive = subscriptionDAO.hasActiveUnexpired(tenantId, module.id);
        if (!alreadyActive) {
            subscriptionDAO.upsertFreeActivation(tenantId, module.id, free.id, ctx.getUserId());
            userTenantDAO.bumpVersionForTenant(tenantId);
        }

        return new FreeActivationResult(module.id, moduleSlug, free.id, free.plan != null ? free.plan.name : null);
    }

    private PlatformModule platformModuleBySlug(String moduleSlug) {
        return platformModuleDAO.findActiveBySlug(moduleSlug)
            .orElseThrow(() -> new NotFoundException("Módulo não encontrado: " + moduleSlug));
    }

    private boolean canActivateFreeModule(TenantContext ctx) {
        String tenantType = tenantDAO.findType(ctx.getTenantId());
        if ("individual".equals(tenantType)) return true;

        if (List.of("owner", "admin").contains(ctx.getUserRole())) return true;

        return profileAccessLevelAdminPermissionDAO
            .memberHasPermission(ctx.getUserId(), ctx.getTenantId(), "plans.subscribe");
    }

    // ─── GET /modules — listar assinaturas do perfil ativo ──────────────────────

    @Override
    public List<ModuleSubscriptionResponse> listModuleSubscriptions(UUID tenantId) {
        return subscriptionDAO.listByTenantOrderByStartedDesc(tenantId).stream()
            .map(row -> new ModuleSubscriptionResponse(
                row.id(), row.tenantId(), profileType(row.tenantType()), row.moduleId(), row.moduleName(),
                row.moduleIconPath(), row.planVersionId(), row.planId(), row.planName(), row.planCode(),
                row.planVersion(), row.planSortOrder(), row.billingCycle(), row.monthlyPrice(),
                row.annualMonthlyPrice(), row.status(), row.startedAt(), row.expiresAt(), row.canceledAt(),
                row.trialDays(), row.trialStartAt(), row.trialEndAt(), row.billingStartsAt(),
                row.trialCampaignId(), row.limitsJson()
            ))
            .toList();
    }

    private static String profileType(String rawTenantType) {
        if (rawTenantType == null) return null;
        return rawTenantType.equalsIgnoreCase("individual") ? "INDIVIDUAL" : "COMPANY";
    }

    // ─── POST /modules/{id}/cancel — cancelar assinatura ────────────────────────

    @Override
    @Transactional
    public ActionResult cancelModuleSubscription(String subscriptionId, TenantContext ctx) {
        if (!CONTRACT_ROLES.contains(ctx.getUserRole())) {
            throw new ForbiddenException("Sem permissão para cancelar assinaturas neste perfil");
        }
        UUID subId = parseUuidOrBadRequest(subscriptionId);

        ProfileModuleSubscription subscription = subscriptionDAO
            .findByIdAndTenantAndStatusIn(subId, ctx.getTenantId(), List.of("ACTIVE", "TRIAL"))
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada ou já cancelada"));

        boolean wasTrial = "TRIAL".equals(subscription.status);
        String newStatus = wasTrial ? "TRIAL_CANCELLED" : "CANCELED";

        // Nunca mexe em expires_at — o acesso permanece válido até lá em ambos os casos.
        subscription.status = newStatus;
        subscription.canceledAt = OffsetDateTime.now();

        if (wasTrial && subscription.trialHistoryId != null) {
            moduleTrialHistoryDAO.markCanceled(subscription.trialHistoryId);
        }

        userTenantDAO.bumpVersionForTenant(ctx.getTenantId());
        return new ActionResult(newStatus);
    }

    // ─── POST /modules/{id}/reactivate — reativar assinatura cancelada ───────────

    @Override
    @Transactional
    public ActionResult reactivateModuleSubscription(String subscriptionId, TenantContext ctx) {
        if (!CONTRACT_ROLES.contains(ctx.getUserRole())) {
            throw new ForbiddenException("Sem permissão para reativar assinaturas neste perfil");
        }
        UUID subId = parseUuidOrBadRequest(subscriptionId);

        ProfileModuleSubscription subscription = subscriptionDAO
            .findByIdAndTenantWithFutureOrNullExpiry(subId, ctx.getTenantId(), List.of("CANCELED", "TRIAL_CANCELLED"))
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada, já ativa ou expirada"));

        boolean wasTrialCancelled = "TRIAL_CANCELLED".equals(subscription.status);
        String restoredStatus = wasTrialCancelled ? "TRIAL" : "ACTIVE";

        subscription.status = restoredStatus;
        subscription.canceledAt = null;

        if (wasTrialCancelled && subscription.trialHistoryId != null) {
            moduleTrialHistoryDAO.markReactivated(subscription.trialHistoryId);
        }

        userTenantDAO.bumpVersionForTenant(ctx.getTenantId());
        return new ActionResult(restoredStatus);
    }

    private static UUID parseUuidOrBadRequest(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("ID inválido");
        }
    }

    // ─── GET /trial-history ──────────────────────────────────────────────────────

    @Override
    public List<TrialHistoryResponse> listTrialHistory(UUID tenantId) {
        return moduleTrialHistoryDAO.listByTenantOrderByStartedDesc(tenantId).stream()
            .map(h -> new TrialHistoryResponse(
                h.moduleId, h.trialCampaignId, h.trialStartedAt, h.trialFinishedAt, h.trialCanceledAt, h.becameCustomer
            ))
            .toList();
    }

    // ─── GET /trial-eligibility ───────────────────────────────────────────────────

    @Override
    public List<TrialEligibilityResponse> listTrialEligibility(UUID tenantId) {
        return planVersionModuleDAO.listActiveOfCurrentPlans().stream()
            .map(pvm -> {
                var elig = trialCampaignNegocio.checkEligibility(tenantId, pvm.moduleId, pvm.id);
                return new TrialEligibilityResponse(pvm.id, pvm.moduleId, elig.eligible(), elig.days(),
                    elig.campaignName(), elig.reasonCode(), elig.cooldownEndsAt());
            })
            .toList();
    }
}
