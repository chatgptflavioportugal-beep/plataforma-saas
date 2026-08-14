package com.saas.subscription.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record TrialEligibilityResponse(
    UUID planVersionModuleId,
    UUID moduleId,
    boolean eligible,
    Integer days,
    String campaignName,
    String reasonCode,
    String cooldownEndsAt
) {}
