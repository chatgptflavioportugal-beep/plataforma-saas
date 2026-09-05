package com.saas.payment.dto.request;

import com.saas.payment.enums.PaymentGateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Solicitação de sessão de checkout hospedado pelo gateway (Stripe Checkout
 * Session, link de cobrança do Asaas) — usada quando o subscription-service
 * quer redirecionar o usuário para uma página de pagamento do próprio gateway.
 */
public record CreateCheckoutRequest(
        UUID subscriptionId,
        UUID customerId,
        String gatewayCustomerId,
        PaymentGateway gateway,
        BigDecimal amount,
        String currency,
        String description,
        String successUrl,
        String cancelUrl,
        String idempotencyKey,
        Map<String, Object> metadata
) {
}
