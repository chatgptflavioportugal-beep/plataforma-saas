package com.saas.subscription.service;

import com.saas.subscription.dto.response.ModuleBillingOptionResponse;
import com.saas.subscription.dto.response.PlanSummaryResponse;
import com.saas.subscription.repository.PlanRepository;
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
public class PlanService {

    @Inject
    PlanRepository planRepository;

    public List<PlanSummaryResponse> listActivePlans(String planType, Integer page, Integer size) {
        return planRepository.listActivePlans(planType, page, size).stream()
            .map(row -> new PlanSummaryResponse(
                row.id(), row.name(), row.code(), row.description(), row.priceMonthly(), row.priceAnnual(),
                row.discountAnnualPercent(), row.maxUsers(), row.maxAiRequestsMonth(), row.sortOrder(),
                row.version(), row.billingType(), row.isMostPopular(), row.planType(), row.totalMonthlyPrice(),
                row.totalAnnualMonthlyPrice(), row.totalAnnualPrice(), row.moduleCount(), row.modulesJson()
            ))
            .toList();
    }

    public List<ModuleBillingOptionResponse> listModuleBillingOptions(Integer page, Integer size) {
        return planRepository.listModuleBillingOptions(page, size).stream()
            .map(row -> new ModuleBillingOptionResponse(
                row.moduleId(), row.moduleName(), row.moduleSlug(), row.moduleDescription(),
                row.iconPath(), row.servicesJson(), row.availablePlansJson()
            ))
            .toList();
    }
}
