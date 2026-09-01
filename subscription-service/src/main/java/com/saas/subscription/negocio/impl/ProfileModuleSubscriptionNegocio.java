package com.saas.subscription.negocio.impl;

import com.saas.subscription.dto.request.ConfirmSubscriptionRequest;
import com.saas.subscription.dto.response.ModuleSubscriptionResponse;
import com.saas.subscription.dto.response.TrialEligibilityResponse;
import com.saas.subscription.dto.response.TrialHistoryResponse;
import com.saas.subscription.security.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Assinaturas de módulo do perfil ativo (tenant), acionadas pelo próprio
 * cliente: contratação, ativação de plano gratuito, listagem, cancelamento,
 * reativação e consulta de Trial. Distinto de AdminSubscriptionNegocio
 * (escopo administrativo sobre qualquer tenant).
 */
public interface ProfileModuleSubscriptionNegocio {

    record ConfirmResult(UUID tenantId, int modulesContracted) {}

    record FreeActivationResult(UUID moduleId, String moduleSlug, UUID planVersionId, String planName) {}

    record ActionResult(String status) {}

    ConfirmResult confirmModuleSubscriptions(ConfirmSubscriptionRequest request, TenantContext ctx);

    FreeActivationResult activateFreeModule(String moduleSlug, TenantContext ctx);

    List<ModuleSubscriptionResponse> listModuleSubscriptions(UUID tenantId);

    ActionResult cancelModuleSubscription(String subscriptionId, TenantContext ctx);

    ActionResult reactivateModuleSubscription(String subscriptionId, TenantContext ctx);

    List<TrialHistoryResponse> listTrialHistory(UUID tenantId);

    List<TrialEligibilityResponse> listTrialEligibility(UUID tenantId);
}
