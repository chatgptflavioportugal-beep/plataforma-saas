package com.saas.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlanModuleWithLimitsRequest(
    String moduleId,
    BigDecimal monthlyPrice,
    BigDecimal annualMonthlyPrice,
    String status,
    Integer sortOrder,
    List<PlanVersionModuleLimitRequest> limits
) {}
