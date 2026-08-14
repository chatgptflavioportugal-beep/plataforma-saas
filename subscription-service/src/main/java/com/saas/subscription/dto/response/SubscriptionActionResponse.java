package com.saas.subscription.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Resposta padrão de cancel/reactivate, tanto em escopo admin quanto de perfil. */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SubscriptionActionResponse(
    boolean success,
    String id,
    String status
) {}
