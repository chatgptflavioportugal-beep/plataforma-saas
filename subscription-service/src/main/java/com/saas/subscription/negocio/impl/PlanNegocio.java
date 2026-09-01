package com.saas.subscription.negocio.impl;

import com.saas.subscription.dto.response.ModuleBillingOptionResponse;
import com.saas.subscription.dto.response.PlanSummaryResponse;

import java.util.List;

/**
 * Leitura pública (SELECT-only) do catálogo de planos, consumida por
 * PublicResource (tela de contratação do cliente). Todo o CRUD administrativo
 * de planos (criar, versionar, editar módulos/limites) vive em admin-service
 * — subscription-service só consome esses dados, nunca os altera.
 */
public interface PlanNegocio {

    List<PlanSummaryResponse> listActivePlans(String planType, Integer page, Integer size);

    List<ModuleBillingOptionResponse> listModuleBillingOptions(Integer page, Integer size);
}
