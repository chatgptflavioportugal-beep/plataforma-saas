package com.saas.payment.provider;

import com.saas.payment.enums.PaymentStatus;

/**
 * Resultado normalizado da interpretação de um evento de webhook, já com a
 * assinatura/autenticidade validada pelo Provider. externalEventId é o
 * identificador único do evento no gateway (usado para deduplicação — ver
 * payment_webhook_events); gatewayPaymentId/gatewaySubscriptionId localizam o
 * registro correspondente em `payments`.
 */
public record WebhookParseResult(
        String externalEventId,
        String eventType,
        String gatewayPaymentId,
        String gatewaySubscriptionId,
        PaymentStatus status
) {
}
