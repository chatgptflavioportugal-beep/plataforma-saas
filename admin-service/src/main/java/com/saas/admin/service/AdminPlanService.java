package com.saas.admin.service;

import com.saas.admin.dao.PlanDAO;
import com.saas.admin.dto.PlanModuleWithLimitsRequest;
import com.saas.admin.dto.PlanRequest;
import com.saas.admin.dto.PlanSummaryDTO;
import com.saas.admin.dto.PlanVersionHistoryDTO;
import com.saas.admin.dto.PlanVersionModuleDTO;
import com.saas.admin.dto.PlanVersionModuleLimitRequest;
import com.saas.admin.dto.PlanVersionModuleRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD administrativo de planos, versionamento e módulos/limitações de cada
 * versão — movido de subscription-service (PlanService) para consolidar em
 * admin-service, único dono das tabelas plans/plan_version_modules/
 * plan_version_module_limits. A leitura pública usada pela tela de
 * contratação do cliente (listActivePlans/listModuleBillingOptions)
 * permanece em subscription-service (subscription-service.PlanService),
 * já que é apenas SELECT sobre dados que o cliente pode consumir.
 */
@ApplicationScoped
public class AdminPlanService {

    @Inject
    PlanDAO dao;

    @Inject
    TrialCampaignAdminService trialCampaignAdminService;

    @Inject
    AdminAuditService auditService;

    // ----------------------------------------------------------------
    // Admin: listagem completa com contagem de assinantes
    // ----------------------------------------------------------------

    public List<PlanSummaryDTO> listAllPlansAdmin() {
        return dao.findAllPlansAdmin();
    }

    // ----------------------------------------------------------------
    // Admin: histórico de versões pelo code
    // ----------------------------------------------------------------

    public List<PlanVersionHistoryDTO> getPlanVersionHistory(String planCode) {
        return dao.findVersionHistory(planCode);
    }

    // ----------------------------------------------------------------
    // Admin: criar novo plano (v1, sem parent)
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> createPlan(PlanRequest req) {
        UUID id = dao.insertPlan(req);
        return Map.of("id", id.toString(), "version", 1, "created", true);
    }

    // ----------------------------------------------------------------
    // Admin: gerar nova versão (preserva plan_type e copia módulos)
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> createNewVersion(String currentPlanId, PlanRequest req, String actorUserId) {
        PlanDAO.CurrentPlanRow current = dao.fetchCurrentPlan(currentPlanId)
            .orElseThrow(() -> new NotFoundException("Plano não encontrado ou já não é a versão atual"));

        dao.deactivateCurrentVersion(currentPlanId);

        UUID newId = UUID.randomUUID();
        int newVersion = current.version() + 1;

        dao.insertNewVersion(
            newId, current.code(),
            req.name() != null ? req.name() : current.name(),
            req.description() != null ? req.description() : current.description(),
            req.discountAnnualPercent() != null ? req.discountAnnualPercent() : current.discountAnnualPercent(),
            req.maxUsers() != null ? req.maxUsers() : current.maxUsers(),
            req.maxAiRequestsMonth() != null ? req.maxAiRequestsMonth() : current.maxAiRequestsMonth(),
            req.sortOrder() != null ? req.sortOrder() : current.sortOrder(),
            newVersion, currentPlanId,
            req.billingType() != null ? req.billingType() : current.billingType(),
            current.isMostPopular(),
            req.planType() != null ? req.planType() : current.planType());

        dao.copyModulesToNewVersion(currentPlanId, newId);
        cancelTrialsForOldVersion(currentPlanId, newVersion, actorUserId);

        return Map.of("id", newId.toString(), "version", newVersion, "new_version_created", true);
    }

    // ----------------------------------------------------------------
    // Admin: marcar como "Mais Popular"
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> setMostPopular(String planId) {
        PlanDAO.PlanPopularEligibility eligibility = dao.fetchPopularEligibility(planId)
            .orElseThrow(() -> new NotFoundException("Plano não encontrado"));

        if (!eligibility.isActive())
            throw new BadRequestException("Planos inativos não podem ser definidos como Mais Popular");
        if (!eligibility.isCurrentVersion())
            throw new BadRequestException("Apenas a versão atual do plano pode ser marcada como Mais Popular");

        dao.clearAllMostPopular();
        dao.setMostPopular(planId);

        return Map.of("id", planId, "is_most_popular", true);
    }

    // ----------------------------------------------------------------
    // Admin: ativar / desativar
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> togglePlanStatus(String planId) {
        int updated = dao.toggleActive(planId);
        if (updated == 0) throw new NotFoundException("Plano não encontrado");

        PlanDAO.PlanActiveFlags flags = dao.fetchActiveFlags(planId)
            .orElseThrow(() -> new NotFoundException("Plano não encontrado"));

        if (!flags.isActive() && flags.isMostPopular()) {
            dao.clearMostPopular(planId);
        }

        return Map.of("id", planId, "is_active", flags.isActive());
    }

    // ----------------------------------------------------------------
    // Módulos de versão do plano — listagem (com limitações)
    // ----------------------------------------------------------------

    public List<PlanVersionModuleDTO> listPlanVersionModules(String planId) {
        return dao.findPlanVersionModules(planId);
    }

    // ----------------------------------------------------------------
    // Módulos de versão do plano — adicionar
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> addPlanVersionModule(String planId, PlanVersionModuleRequest req) {
        if (dao.countPlans(planId) == 0) throw new NotFoundException("Plano não encontrado");

        checkNoSubscribers(planId);

        if (req.moduleId() == null || req.moduleId().isBlank())
            throw new BadRequestException("moduleId é obrigatório");

        if (dao.countDuplicateModule(planId, req.moduleId()) > 0)
            throw new BadRequestException("Este módulo já está adicionado a esta versão do plano");

        UUID id = dao.insertPlanVersionModule(planId, req);
        return Map.of("id", id.toString(), "created", true);
    }

    // ----------------------------------------------------------------
    // Módulos de versão do plano — atualizar
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> updatePlanVersionModule(String pvmId, PlanVersionModuleRequest req) {
        String planId = getPlanIdFromPvm(pvmId);
        checkNoSubscribers(planId);

        int updated = dao.updatePlanVersionModule(pvmId, req);
        if (updated == 0) throw new NotFoundException("Módulo não encontrado neste plano");
        return Map.of("id", pvmId, "updated", true);
    }

    // ----------------------------------------------------------------
    // Módulos de versão do plano — remover
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> removePlanVersionModule(String pvmId) {
        String planId = getPlanIdFromPvm(pvmId);
        checkNoSubscribers(planId);

        int deleted = dao.deletePlanVersionModule(pvmId);
        if (deleted == 0) throw new NotFoundException("Módulo não encontrado neste plano");
        return Map.of("id", pvmId, "deleted", true);
    }

    // ----------------------------------------------------------------
    // Limitações do módulo — adicionar
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> addPlanVersionModuleLimit(String pvmId, PlanVersionModuleLimitRequest req) {
        if (req.title() == null || req.title().isBlank())
            throw new BadRequestException("title é obrigatório");

        if (dao.countPvm(pvmId) == 0) throw new NotFoundException("Módulo do plano não encontrado");

        UUID id = dao.insertLimit(pvmId, req);
        return Map.of("id", id.toString(), "created", true);
    }

    // ----------------------------------------------------------------
    // Limitações do módulo — atualizar
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> updatePlanVersionModuleLimit(String limitId, PlanVersionModuleLimitRequest req) {
        if (req.title() == null || req.title().isBlank())
            throw new BadRequestException("title é obrigatório");

        int updated = dao.updateLimit(limitId, req);
        if (updated == 0) throw new NotFoundException("Limitação não encontrada");
        return Map.of("id", limitId, "updated", true);
    }

    // ----------------------------------------------------------------
    // Limitações do módulo — remover
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> removePlanVersionModuleLimit(String limitId) {
        int deleted = dao.deleteLimit(limitId);
        if (deleted == 0) throw new NotFoundException("Limitação não encontrada");
        return Map.of("id", limitId, "deleted", true);
    }

    // ----------------------------------------------------------------
    // Admin: gerar nova versão com módulos completos (edição unificada)
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> createNewVersionWithModules(
            String currentPlanId,
            PlanRequest req,
            List<PlanModuleWithLimitsRequest> modules,
            String actorUserId) {

        PlanDAO.CurrentPlanRow current = dao.fetchCurrentPlan(currentPlanId)
            .orElseThrow(() -> new NotFoundException("Plano não encontrado ou já não é a versão atual"));

        dao.deactivateCurrentVersion(currentPlanId);

        UUID newId = UUID.randomUUID();
        int newVersion = current.version() + 1;

        dao.insertNewVersion(
            newId, current.code(),
            req.name() != null ? req.name() : current.name(),
            req.description() != null ? req.description() : current.description(),
            req.discountAnnualPercent() != null ? req.discountAnnualPercent() : current.discountAnnualPercent(),
            req.maxUsers() != null ? req.maxUsers() : current.maxUsers(),
            req.maxAiRequestsMonth() != null ? req.maxAiRequestsMonth() : current.maxAiRequestsMonth(),
            req.sortOrder() != null ? req.sortOrder() : current.sortOrder(),
            newVersion, currentPlanId,
            req.billingType() != null ? req.billingType() : current.billingType(),
            current.isMostPopular(),
            req.planType() != null ? req.planType() : current.planType());

        if (modules != null) {
            for (PlanModuleWithLimitsRequest mod : modules) {
                UUID pvmId = UUID.randomUUID();
                dao.insertPlanVersionModule(pvmId, newId.toString(), new PlanVersionModuleRequest(
                    mod.moduleId(), mod.monthlyPrice(), mod.annualMonthlyPrice(), mod.status(), mod.sortOrder()));

                if (mod.limits() != null) {
                    for (PlanVersionModuleLimitRequest lim : mod.limits()) {
                        dao.insertLimit(UUID.randomUUID(), pvmId.toString(), lim);
                    }
                }
            }
        } else {
            dao.copyModulesToNewVersion(currentPlanId, newId);
        }
        cancelTrialsForOldVersion(currentPlanId, newVersion, actorUserId);

        return Map.of("id", newId.toString(), "version", newVersion, "new_version_created", true);
    }

    // ----------------------------------------------------------------
    // Helper privado — cancela as Trial Campaigns ACTIVE/SCHEDULED da versão antiga
    // ao gerar uma nova versão do plano. A campanha promovia especificamente aquela
    // versão antiga, então não é levada adiante — apenas novas adesões são bloqueadas,
    // participantes que já entraram continuam normalmente (ver TrialCampaignAdminService).
    // ----------------------------------------------------------------

    private void cancelTrialsForOldVersion(String oldPlanId, int newVersion, String actorUserId) {
        String reason = "Plano substituído automaticamente pela versão v" + newVersion + ".";
        List<UUID> cancelledIds = trialCampaignAdminService.cancelCampaignsForPlanVersion(
            oldPlanId, reason, UUID.fromString(actorUserId));

        for (UUID campaignId : cancelledIds) {
            auditService.log(actorUserId, "trial_campaign.plan_version_replaced",
                "trial_campaigns", campaignId.toString(),
                Map.of("reason", reason, "oldPlanId", oldPlanId, "newPlanVersion", newVersion));
        }
    }

    // ----------------------------------------------------------------
    // Helper privado — obtém plan_id a partir do pvmId (lança 404 se não existe)
    // ----------------------------------------------------------------

    private String getPlanIdFromPvm(String pvmId) {
        return dao.findPlanIdForPvm(pvmId)
            .orElseThrow(() -> new NotFoundException("Módulo não encontrado neste plano"));
    }

    // ----------------------------------------------------------------
    // Helper privado — bloqueia alteração se a versão do plano já tem assinantes
    // ----------------------------------------------------------------

    private void checkNoSubscribers(String planId) {
        long subscribers = dao.countSubscribers(planId);
        if (subscribers > 0)
            throw new BadRequestException(
                "Esta versão possui " + subscribers + " assinante(s). " +
                "Crie uma nova versão para alterar os módulos."
            );
    }
}
