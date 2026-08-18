package com.saas.admin.dto;

public record PlanVersionModuleLimitRequest(
    String title,
    String description,
    String code,
    String limitValue,
    String unit,
    Integer sortOrder
) {}
