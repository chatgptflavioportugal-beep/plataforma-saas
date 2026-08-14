package com.saas.subscription.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record DashboardServiceResponse(
    UUID serviceId,
    String serviceName,
    String serviceSlug,
    String serviceDescription,
    String serviceIconPath,
    UUID serviceGroupId,
    String serviceGroupName,
    String routeKey,
    boolean hasAccess
) {}
