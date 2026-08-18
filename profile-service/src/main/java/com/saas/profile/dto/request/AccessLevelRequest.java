package com.saas.profile.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Corpo de criação/edição de um nível de acesso (mesmo formato para POST e PUT).
 * {@code serviceIds}/{@code adminPermissionKeys} já chegam em camelCase do frontend —
 * LowerCamelCaseStrategy evita que a naming strategy global (snake_case) rejeite o corpo.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record AccessLevelRequest(
        String name,
        String description,
        List<String> serviceIds,
        List<String> adminPermissionKeys
) {}
