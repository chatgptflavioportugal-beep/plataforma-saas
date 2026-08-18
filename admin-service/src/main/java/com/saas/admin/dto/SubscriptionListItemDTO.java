package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SubscriptionListItemDTO(
        String id,
        String profileId,
        String profileName,
        String profileType,
        String companyId,
        String companyName,
        String companySlug,
        String ownerUserId,
        String ownerName,
        String ownerEmail,
        String moduleId,
        String moduleName,
        String moduleIconPath,
        String planId,
        String planName,
        String planVersionId,
        Integer planVersionNumber,
        String billingCycle,
        BigDecimal price,
        BigDecimal annualTotalPrice,
        String status,
        String startedAt,
        String expiresAt,
        String canceledAt,
        Boolean renewalActive) {
}
