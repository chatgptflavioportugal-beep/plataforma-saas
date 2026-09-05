package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** {@code payload} é o JSON bruto (texto) recebido do gateway — somente leitura. */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PaymentEventDetailDTO(
        String id,
        String paymentId,
        String gateway,
        String externalEventId,
        String eventType,
        String status,
        String errorMessage,
        int attempts,
        String createdAt,
        String processedAt,
        String payload) {
}
