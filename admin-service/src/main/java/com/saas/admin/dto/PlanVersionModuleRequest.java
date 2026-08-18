package com.saas.admin.dto;

import java.math.BigDecimal;

public record PlanVersionModuleRequest(
    String moduleId,
    BigDecimal monthlyPrice,
    BigDecimal annualMonthlyPrice,
    String status,
    Integer sortOrder
) {}
