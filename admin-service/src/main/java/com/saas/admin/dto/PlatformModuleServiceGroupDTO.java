package com.saas.admin.dto;

public record PlatformModuleServiceGroupDTO(
        String id,
        String moduleId,
        String name,
        String slug,
        String description,
        String iconPath,
        Integer sortOrder,
        String status,
        String createdAt,
        String updatedAt,
        Integer serviceCount) {
}
