package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Sem o payload — mantém a listagem de histórico leve; ver PaymentEventDetailDTO. */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PaymentEventListItemDTO(
        String id,
        String gateway,
        String externalEventId,
        String eventType,
        String status,
        String errorMessage,
        int attempts,
        String createdAt,
        String processedAt) {
}
