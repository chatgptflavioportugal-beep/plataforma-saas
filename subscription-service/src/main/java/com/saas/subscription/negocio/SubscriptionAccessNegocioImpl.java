package com.saas.subscription.negocio;

import com.saas.subscription.negocio.impl.SubscriptionAccessNegocio;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;

/**
 * Regra única de "esta assinatura de módulo está utilizável agora?", reutilizada
 * pelos pontos que decidem acesso em tempo real (dashboard, emissão de token de
 * módulo, resolução de rota de serviço) para não reimplementar a mesma condição
 * em SQL/Java de formas ligeiramente diferentes em cada lugar.
 */
@ApplicationScoped
public class SubscriptionAccessNegocioImpl implements SubscriptionAccessNegocio {

    @Override
    public boolean isSubscriptionUsable(String status, OffsetDateTime expiresAt) {
        boolean statusOk = status != null &&
            (status.equals("ACTIVE") || status.equals("TRIAL") || status.equals("TRIAL_CANCELLED"));
        boolean notExpired = expiresAt == null || expiresAt.isAfter(OffsetDateTime.now());
        return statusOk && notExpired;
    }
}
