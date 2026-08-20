package com.saas.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contrato de saída de /api/v1/services/resolve-route/{routeKey}, consumido pelo
 * frontend-host (ver ResolvedServiceRoute em frontend-host/src/shared/types/index.ts).
 * @JsonProperty fixa os nomes em camelCase, sobrepondo o
 * quarkus.jackson.property-naming-strategy=SNAKE_CASE global — mudar esses nomes quebra
 * o contrato já consumido pelo frontend.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceRouteResolutionDTO(
        @JsonProperty("serviceId") String serviceId,
        @JsonProperty("serviceName") String serviceName,
        @JsonProperty("routeKey") String routeKey,
        @JsonProperty("permissionKey") String permissionKey,
        @JsonProperty("moduleId") String moduleId,
        @JsonProperty("moduleName") String moduleName,
        @JsonProperty("moduleSlug") String moduleSlug,
        @JsonProperty("accessStatus") String accessStatus
) {

    public static ServiceRouteResolutionDTO notFound(String routeKey) {
        return new ServiceRouteResolutionDTO(
                null, null, routeKey, null, null, null, null, "NOT_FOUND");
    }

    public static ServiceRouteResolutionDTO found(String serviceId, String serviceName,
            String routeKey, String permissionKey, String moduleId, String moduleName,
            String moduleSlug) {
        return new ServiceRouteResolutionDTO(
                serviceId, serviceName, routeKey, permissionKey, moduleId, moduleName,
                moduleSlug, "FOUND");
    }
}
