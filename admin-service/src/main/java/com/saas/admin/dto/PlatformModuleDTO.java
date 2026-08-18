package com.saas.admin.dto;

public record PlatformModuleDTO(
        String id,
        String name,
        String slug,
        String description,
        String moduleUrl,
        String iconPath,
        Boolean isActive,
        Integer sortOrder,
        String createdAt,
        String updatedAt,
        Integer serviceCount) {
}
