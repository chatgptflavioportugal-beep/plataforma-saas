package com.saas.subscription.negocio;

import com.saas.subscription.dao.PlanDAO;
import com.saas.subscription.dto.response.ModuleBillingOptionResponse;
import com.saas.subscription.dto.response.PlanSummaryResponse;
import com.saas.subscription.negocio.impl.PlanNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Leitura pública (SELECT-only) do catálogo de planos, consumida por
 * PublicResource (tela de contratação do cliente). Todo o CRUD administrativo
 * de planos (criar, versionar, editar módulos/limites) vive em admin-service
 * — subscription-service só consome esses dados, nunca os altera.
 */
@ApplicationScoped
public class PlanNegocioImpl implements PlanNegocio {

    @Inject
    PlanDAO planDAO;

    @Override
    public List<PlanSummaryResponse> listActivePlans(String planType, Integer page, Integer size) {
        return planDAO.listActivePlans(planType, page, size).stream()
            .map(row -> new PlanSummaryResponse(
                row.id(), row.name(), row.code(), row.description(), row.priceMonthly(), row.priceAnnual(),
                row.discountAnnualPercent(), row.maxUsers(), row.maxAiRequestsMonth(), row.sortOrder(),
                row.version(), row.billingType(), row.isMostPopular(), row.planType(), row.totalMonthlyPrice(),
                row.totalAnnualMonthlyPrice(), row.totalAnnualPrice(), row.moduleCount(), row.modulesJson()
            ))
            .toList();
    }

    @Override
    public List<ModuleBillingOptionResponse> listModuleBillingOptions(Integer page, Integer size) {
        return planDAO.listModuleBillingOptions(page, size).stream()
            .map(row -> new ModuleBillingOptionResponse(
                row.moduleId(), row.moduleName(), row.moduleSlug(), row.moduleDescription(),
                row.iconPath(), row.servicesJson(), row.availablePlansJson()
            ))
            .toList();
    }
}
