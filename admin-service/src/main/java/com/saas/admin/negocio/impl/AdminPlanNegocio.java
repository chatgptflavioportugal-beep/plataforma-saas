package com.saas.admin.negocio.impl;

import com.saas.admin.dto.PlanModuleWithLimitsRequest;
import com.saas.admin.dto.PlanRequest;
import com.saas.admin.dto.PlanSummaryDTO;
import com.saas.admin.dto.PlanVersionHistoryDTO;
import com.saas.admin.dto.PlanVersionModuleDTO;
import com.saas.admin.dto.PlanVersionModuleLimitRequest;
import com.saas.admin.dto.PlanVersionModuleRequest;

import java.util.List;
import java.util.Map;

/**
 * CRUD administrativo de planos, versionamento e módulos/limitações de cada
 * versão — movido de subscription-service (PlanService) para consolidar em
 * admin-service, único dono das tabelas plans/plan_version_modules/
 * plan_version_module_limits. A leitura pública usada pela tela de
 * contratação do cliente (listActivePlans/listModuleBillingOptions)
 * permanece em subscription-service (subscription-service.PlanService),
 * já que é apenas SELECT sobre dados que o cliente pode consumir.
 */
public interface AdminPlanNegocio {

    // ----------------------------------------------------------------
    // Admin: listagem completa com contagem de assinantes
    // ----------------------------------------------------------------

    List<PlanSummaryDTO> listAllPlansAdmin();

    // ----------------------------------------------------------------
    // Admin: histórico de versões pelo code
    // ----------------------------------------------------------------

    List<PlanVersionHistoryDTO> getPlanVersionHistory(String planCode);

    // ----------------------------------------------------------------
    // Admin: criar novo plano (v1, sem parent)
    // ----------------------------------------------------------------

    Map<String, Object> createPlan(PlanRequest req);

    // ----------------------------------------------------------------
    // Admin: gerar nova versão (preserva plan_type e copia módulos)
    // ----------------------------------------------------------------

    Map<String, Object> createNewVersion(String currentPlanId, PlanRequest req, String actorUserId);

    // ----------------------------------------------------------------
    // Admin: marcar como "Mais Popular"
    // ----------------------------------------------------------------

    Map<String, Object> setMostPopular(String planId);

    // ----------------------------------------------------------------
    // Admin: ativar / desativar
    // ----------------------------------------------------------------

    Map<String, Object> togglePlanStatus(String planId);

    // ----------------------------------------------------------------
    // Módulos de versão do plano — listagem (com limitações)
    // ----------------------------------------------------------------

    List<PlanVersionModuleDTO> listPlanVersionModules(String planId);

    // ----------------------------------------------------------------
    // Módulos de versão do plano — adicionar
    // ----------------------------------------------------------------

    Map<String, Object> addPlanVersionModule(String planId, PlanVersionModuleRequest req);

    // ----------------------------------------------------------------
    // Módulos de versão do plano — atualizar
    // ----------------------------------------------------------------

    Map<String, Object> updatePlanVersionModule(String pvmId, PlanVersionModuleRequest req);

    // ----------------------------------------------------------------
    // Módulos de versão do plano — remover
    // ----------------------------------------------------------------

    Map<String, Object> removePlanVersionModule(String pvmId);

    // ----------------------------------------------------------------
    // Limitações do módulo — adicionar
    // ----------------------------------------------------------------

    Map<String, Object> addPlanVersionModuleLimit(String pvmId, PlanVersionModuleLimitRequest req);

    // ----------------------------------------------------------------
    // Limitações do módulo — atualizar
    // ----------------------------------------------------------------

    Map<String, Object> updatePlanVersionModuleLimit(String limitId, PlanVersionModuleLimitRequest req);

    // ----------------------------------------------------------------
    // Limitações do módulo — remover
    // ----------------------------------------------------------------

    Map<String, Object> removePlanVersionModuleLimit(String limitId);

    // ----------------------------------------------------------------
    // Admin: gerar nova versão com módulos completos (edição unificada)
    // ----------------------------------------------------------------

    Map<String, Object> createNewVersionWithModules(
            String currentPlanId,
            PlanRequest req,
            List<PlanModuleWithLimitsRequest> modules,
            String actorUserId);
}
