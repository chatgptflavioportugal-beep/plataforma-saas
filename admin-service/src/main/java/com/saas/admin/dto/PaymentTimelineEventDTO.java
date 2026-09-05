package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Um ponto na timeline do pagamento — construída exclusivamente a partir de
 * fatos reais (a criação do Payment + cada PaymentWebhookEvent registrado),
 * nunca de passos fictícios (ver AdminPaymentNegocioImpl.getTimeline).
 *
 * {@code kind}: PAYMENT_CREATED ou WEBHOOK_EVENT.
 * {@code eventType}: null quando kind = PAYMENT_CREATED.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PaymentTimelineEventDTO(
        String kind,
        String eventType,
        String status,
        String occurredAt,
        String processedAt) {
}
