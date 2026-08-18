package com.saas.profile.dto.tenant;

public record SubscriptionDto(
        String id,
        String status,
        String trialEnd,
        String currentPeriodStart,
        String currentPeriodEnd,
        String billingType,
        Object planVersion
) {}
