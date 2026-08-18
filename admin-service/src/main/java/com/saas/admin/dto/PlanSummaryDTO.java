package com.saas.admin.dto;

import java.math.BigDecimal;

public record PlanSummaryDTO(
        String id,
        String name,
        String code,
        String description,
        BigDecimal priceMonthly,
        BigDecimal priceAnnual,
        Integer discountAnnualPercent,
        Integer maxUsers,
        Integer maxAiRequestsMonth,
        Boolean isActive,
        Integer sortOrder,
        Integer version,
        Boolean isCurrentVersion,
        String parentPlanId,
        String billingType,
        String createdAt,
        Boolean isMostPopular,
        String planType,
        Long paidSubscriptions,
        Long trialSubscriptions,
        Long subscriberCount,
        BigDecimal totalMonthlyPrice,
        BigDecimal totalAnnualMonthlyPrice,
        BigDecimal totalAnnualPrice,
        Integer moduleCount) {
}
