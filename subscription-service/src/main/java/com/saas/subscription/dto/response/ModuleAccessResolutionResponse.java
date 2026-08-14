package com.saas.subscription.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;
import java.util.UUID;

/**
 * resolution: GRANTED | MODULE_NOT_FOUND | MODULE_EXPIRED | FREE_PLAN_NOT_ACTIVATED | NO_ACCESS.
 * Campos nulos são omitidos (NON_NULL) para preservar o corpo enxuto por
 * resolução que o auth-service (consumidor interno) já espera.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModuleAccessResolutionResponse(
    String resolution,
    UUID moduleId,
    String moduleName,
    String planName,
    String accessSource,
    UUID planVersionId,
    Map<String, Object> limits
) {
    public static ModuleAccessResolutionResponse notFound() {
        return new ModuleAccessResolutionResponse("MODULE_NOT_FOUND", null, null, null, null, null, null);
    }

    public static ModuleAccessResolutionResponse expired(UUID moduleId, String moduleName) {
        return new ModuleAccessResolutionResponse("MODULE_EXPIRED", moduleId, moduleName, null, null, null, null);
    }

    public static ModuleAccessResolutionResponse freePlanNotActivated(UUID moduleId, String moduleName, UUID planVersionId) {
        return new ModuleAccessResolutionResponse("FREE_PLAN_NOT_ACTIVATED", moduleId, moduleName, null, null, planVersionId, null);
    }

    public static ModuleAccessResolutionResponse noAccess(UUID moduleId, String moduleName) {
        return new ModuleAccessResolutionResponse("NO_ACCESS", moduleId, moduleName, null, null, null, null);
    }

    public static ModuleAccessResolutionResponse granted(UUID moduleId, String moduleName, String planName,
                                                           String accessSource, Map<String, Object> limits) {
        return new ModuleAccessResolutionResponse("GRANTED", moduleId, moduleName, planName, accessSource, null, limits);
    }
}
