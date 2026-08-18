package com.saas.profile.dto.accesslevel;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Wire format camelCase preservado deliberadamente (diverge da naming strategy global
 * SNAKE_CASE) — é o contrato já consumido hoje pelo frontend para este endpoint.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ServiceDto(
        String serviceId,
        String serviceName,
        String serviceSlug,
        String serviceIconPath,
        Integer sortOrder
) {}
