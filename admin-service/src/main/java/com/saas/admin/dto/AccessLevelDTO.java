package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Frontend consome estes campos em lowerCamelCase (permCount, userCount,
 * createdAt, updatedAt) — o Resource original montava um Map manualmente com
 * essas chaves literais, contornando a estratégia global SNAKE_CASE do
 * Jackson. @JsonNaming preserva o mesmo contrato para este record.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record AccessLevelDTO(
        String id,
        String name,
        String description,
        String status,
        String createdAt,
        String updatedAt,
        Integer permCount,
        Integer userCount) {
}
