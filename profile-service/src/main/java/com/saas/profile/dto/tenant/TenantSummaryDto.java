package com.saas.profile.dto.tenant;

public record TenantSummaryDto(
        String id,
        String name,
        String slug,
        String status,
        String type,
        String planId,
        String trialEndsAt,
        String createdAt,
        String updatedAt
) {}
