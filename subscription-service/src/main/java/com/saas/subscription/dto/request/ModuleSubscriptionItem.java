package com.saas.subscription.dto.request;

public record ModuleSubscriptionItem(
    String moduleId,
    String planVersionId,
    String billingCycle
) {}
