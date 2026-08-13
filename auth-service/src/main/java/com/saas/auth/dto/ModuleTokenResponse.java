package com.saas.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * Corpo de sucesso de POST /api/v1/module-token/{moduleSlug}. Naming pinado
 * em camelCase (contrato já consumido pelo frontend) — independente do
 * quarkus.jackson.property-naming-strategy=SNAKE_CASE global.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ModuleTokenResponse(
        String moduleAccessToken,
        String moduleSlug,
        String moduleName,
        String planName,
        String expiresAt,
        List<String> permissions,
        Map<String, Object> limits
) {
}
