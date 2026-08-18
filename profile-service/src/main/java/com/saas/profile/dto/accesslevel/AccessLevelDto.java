package com.saas.profile.dto.accesslevel;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record AccessLevelDto(
        String id,
        String name,
        String description,
        String status,
        String createdAt,
        String updatedAt,
        List<AccessLevelPermissionDto> permissions,
        List<String> adminPermissions,
        long memberCount
) {}
