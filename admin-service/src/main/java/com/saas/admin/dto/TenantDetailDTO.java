package com.saas.admin.dto;

import java.util.List;

public record TenantDetailDTO(
        String id,
        String name,
        String slug,
        String status,
        String createdAt,
        String trialEndsAt,
        String planName,
        String planCode,
        String subscriptionStatus,
        String trialStart,
        String trialEnd,
        String currentPeriodStart,
        String currentPeriodEnd,
        String billingType,
        String ownerName,
        String ownerEmail,
        String ownerJoinedAt,
        List<TenantMemberDTO> members,
        List<TenantInvitationDTO> pendingInvitations) {
}
