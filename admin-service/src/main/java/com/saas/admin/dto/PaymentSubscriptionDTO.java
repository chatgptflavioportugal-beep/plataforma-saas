package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PaymentSubscriptionDTO(
        String id,
        String moduleId,
        String moduleName,
        String planId,
        String planName,
        String billingCycle,
        String status,
        String startedAt,
        String expiresAt) {
}
