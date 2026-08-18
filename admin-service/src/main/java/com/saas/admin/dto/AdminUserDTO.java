package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record AdminUserDTO(
        String id,
        String email,
        String fullName,
        String systemRole,
        Boolean isActive,
        String accessLevelId,
        String accessLevelName,
        String createdAt,
        String lastSignInAt) {
}
