package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PaymentListItemDTO(
        String id,
        String externalId,
        String subscriptionId,
        String customerId,
        String customerName,
        String customerEmail,
        String moduleId,
        String moduleName,
        String type,
        String billingCycle,
        String gateway,
        String paymentMethod,
        BigDecimal amount,
        String currency,
        String status,
        String createdAt,
        String lastEventType,
        String lastEventStatus,
        String lastEventAt,
        boolean hasWebhookError) {
}
