package com.saas.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Corpo de GET /api/v1/profile/permissions-version. Naming pinado em
 * camelCase (contrato já consumido pelo frontend) — independente do
 * quarkus.jackson.property-naming-strategy=SNAKE_CASE global.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PermissionsVersionResponse(int permissionsVersion) {
}
