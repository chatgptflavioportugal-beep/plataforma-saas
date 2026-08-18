package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record AccessLevelDetailDTO(
        String id,
        String name,
        String description,
        String status,
        String createdAt,
        String updatedAt,
        List<String> permissionKeys) {
}
