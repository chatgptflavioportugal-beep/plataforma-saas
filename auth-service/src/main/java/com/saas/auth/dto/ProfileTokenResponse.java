package com.saas.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Corpo de sucesso de POST /api/v1/profile/access-token. Naming pinado em
 * camelCase (contrato já consumido pelo frontend) — independente do
 * quarkus.jackson.property-naming-strategy=SNAKE_CASE global.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ProfileTokenResponse(
        String profileAccessToken,
        String expiresAt,
        List<String> permissions
) {
}
