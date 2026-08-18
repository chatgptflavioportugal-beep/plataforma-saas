package com.saas.admin.dto;

public record ModuleServiceGroupRequest(
    String name,
    String slug,
    String description,
    String iconPath,
    Integer sortOrder,
    String status
) {}
