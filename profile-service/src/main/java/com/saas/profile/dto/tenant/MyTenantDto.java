package com.saas.profile.dto.tenant;

public record MyTenantDto(String id, String userId, String tenantId, String role, boolean isActive, TenantSummaryDto tenant) {}
