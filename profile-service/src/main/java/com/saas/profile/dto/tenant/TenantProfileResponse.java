package com.saas.profile.dto.tenant;

public record TenantProfileResponse(TenantDto tenant, SubscriptionDto subscription, PlanDto plan, String role) {}
