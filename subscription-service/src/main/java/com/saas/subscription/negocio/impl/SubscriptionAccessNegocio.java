package com.saas.subscription.negocio.impl;

import java.time.OffsetDateTime;

/**
 * Regra única de "esta assinatura de módulo está utilizável agora?", reutilizada
 * pelos pontos que decidem acesso em tempo real (dashboard, emissão de token de
 * módulo, resolução de rota de serviço) para não reimplementar a mesma condição
 * em SQL/Java de formas ligeiramente diferentes em cada lugar.
 */
public interface SubscriptionAccessNegocio {

    boolean isSubscriptionUsable(String status, OffsetDateTime expiresAt);
}
