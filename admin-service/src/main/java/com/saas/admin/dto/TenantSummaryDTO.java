package com.saas.admin.dto;

public record TenantSummaryDTO(
        String id,
        String name,
        String slug,
        String status,
        String createdAt,
        String trialEndsAt,
        String planName,
        String planCode,
        String subscriptionStatus,
        String ownerName,
        String ownerEmail,
        Integer memberCount,
        Integer pendingInvitationsCount) {
}
