package com.saas.admin.dto;

public record ModuleServiceRequest(
    String name,
    String slug,
    String description,
    String iconPath,
    Boolean isActive,
    Integer sortOrder,
    String serviceGroupId
) {}
