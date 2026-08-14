package com.saas.subscription.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record DashboardModuleResponse(
    UUID moduleId,
    String moduleName,
    String moduleSlug,
    String moduleDescription,
    String moduleIconPath,
    String accessStatus,
    String planName,
    String planSlug,
    UUID planVersionId,
    String badgeLabel,
    long serviceCount,
    String expiresAt,
    Integer trialDaysRemaining,
    boolean trialCancelled,
    List<DashboardServiceResponse> services
) {}
