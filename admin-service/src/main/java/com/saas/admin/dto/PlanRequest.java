package com.saas.admin.dto;

import java.math.BigDecimal;

public record PlanRequest(
    String name,
    String code,
    String description,
    BigDecimal priceMonthly,
    BigDecimal priceAnnual,
    Integer discountAnnualPercent,
    Integer maxUsers,
    Integer maxAiRequestsMonth,
    String billingType,
    Integer sortOrder,
    String planType
) {}
