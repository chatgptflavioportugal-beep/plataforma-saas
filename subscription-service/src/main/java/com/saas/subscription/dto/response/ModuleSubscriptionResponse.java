package com.saas.subscription.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ModuleSubscriptionResponse(
    UUID id,
    UUID profileId,
    String profileType,
    UUID moduleId,
    String moduleName,
    String moduleIconPath,
    UUID planVersionId,
    UUID planId,
    String planName,
    String planCode,
    Integer planVersion,
    Integer planSortOrder,
    String billingCycle,
    BigDecimal monthlyPrice,
    BigDecimal annualMonthlyPrice,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime expiresAt,
    OffsetDateTime canceledAt,
    Integer trialDays,
    OffsetDateTime trialStartAt,
    OffsetDateTime trialEndAt,
    OffsetDateTime billingStartsAt,
    UUID trialCampaignId,
    String limitsJson
) {}
