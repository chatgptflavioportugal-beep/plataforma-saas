package com.saas.subscription.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ConfirmSubscriptionResponse(
    boolean success,
    String tenantId,
    int modulesContracted
) {}
