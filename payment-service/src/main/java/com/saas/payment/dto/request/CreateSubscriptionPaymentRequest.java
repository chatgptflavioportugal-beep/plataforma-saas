package com.saas.payment.dto.request;

import com.saas.payment.enums.PaymentGateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Solicitação de cobrança recorrente (assinatura no gateway). billingCycle é
 * repassado como veio do subscription-service ("MONTHLY"/"ANNUAL") apenas
 * para o Provider decidir o intervalo de cobrança no gateway — o Payment
 * Service não interpreta regra de plano a partir disso.
 */
public record CreateSubscriptionPaymentRequest(
        UUID subscriptionId,
        UUID customerId,
        String gatewayCustomerId,
        PaymentGateway gateway,
        BigDecimal amount,
        String currency,
        String billingCycle,
        String paymentMethodId,
        String description,
        String idempotencyKey,
        Map<String, Object> metadata
) {
}
