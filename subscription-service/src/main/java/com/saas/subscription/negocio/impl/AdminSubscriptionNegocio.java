package com.saas.subscription.negocio.impl;

import java.util.UUID;

/**
 * Ações administrativas sobre o ciclo de vida de assinaturas — cancelar/
 * reativar em nome de um administrador da plataforma agindo sobre a
 * assinatura de qualquer tenant. Distinto de ProfileModuleSubscriptionNegocio
 * (tenant-scoped, acionado pelo próprio cliente sobre a própria assinatura).
 */
public interface AdminSubscriptionNegocio {

    record ActionResult(UUID tenantId, String status) {}

    ActionResult cancel(String subscriptionId, UUID adminUserId);

    ActionResult reactivate(String subscriptionId, UUID adminUserId);
}
