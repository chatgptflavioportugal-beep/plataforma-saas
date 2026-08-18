package com.saas.admin.dto;

import java.math.BigDecimal;

public record PlanVersionModuleDTO(
        String id,
        String planId,
        String moduleId,
        String moduleName,
        String moduleSlug,
        String moduleIconPath,
        BigDecimal monthlyPrice,
        BigDecimal annualMonthlyPrice,
        String status,
        Integer sortOrder,
        String createdAt,
        String updatedAt,
        String limitsJson) {
}
