package com.saas.auth.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * Corpo da resposta do subscription-service (GET /api/v1/internal/module-access/{slug}).
 * Sempre HTTP 200; o campo {@code resolution} é que carrega o resultado
 * (GRANTED / MODULE_NOT_FOUND / MODULE_EXPIRED / FREE_PLAN_NOT_ACTIVATED / NO_ACCESS).
 * Serializado do outro lado como Map.of(...) — chaves camelCase literais, não
 * afetadas pelo SNAKE_CASE global; por isso o naming strategy é fixado aqui.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ModuleAccessResolution(
        String resolution,
        String moduleId,
        String moduleName,
        String planName,
        String accessSource,
        Map<String, Object> limits,
        String planVersionId
) {
}
