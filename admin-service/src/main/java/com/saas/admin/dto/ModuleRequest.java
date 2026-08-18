package com.saas.admin.dto;

public record ModuleRequest(
    String name,
    String slug,
    String description,
    String moduleUrl,
    String iconPath,
    Boolean isActive,
    Integer sortOrder
) {}
