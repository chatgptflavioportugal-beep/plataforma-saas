package com.saas.admin.dto;

public record PlatformModuleServiceDTO(
        String id,
        String moduleId,
        String name,
        String slug,
        String description,
        String iconPath,
        Boolean isActive,
        Integer sortOrder,
        String createdAt,
        String updatedAt,
        String serviceGroupId,
        String serviceGroupName,
        String serviceGroupSlug,
        String routeKey) {
}
