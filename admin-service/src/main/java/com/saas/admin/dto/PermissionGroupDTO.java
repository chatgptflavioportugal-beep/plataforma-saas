package com.saas.admin.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PermissionGroupDTO(String groupKey, String groupName, List<PermissionDTO> permissions) {

    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record PermissionDTO(String permissionKey, String label) {
    }
}
