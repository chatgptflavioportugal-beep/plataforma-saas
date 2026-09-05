package com.saas.payment.dto.request;

import com.saas.payment.enums.PaymentGateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Solicitação de cobrança avulsa (não recorrente), feita pelo subscription-service
 * em nome de um tenant já autorizado. Nunca carrega dado sensível de cartão —
 * apenas identificadores já emitidos pelo gateway (ex.: paymentMethodId) quando aplicável.
 */
public record CreatePaymentRequest(
        UUID subscriptionId,
        UUID customerId,
        String gatewayCustomerId,
        PaymentGateway gateway,
        BigDecimal amount,
        String currency,
        String paymentMethodId,
        String description,
        String idempotencyKey,
        Map<String, Object> metadata
) {
}
