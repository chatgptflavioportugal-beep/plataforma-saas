package com.saas.profile.dto.member;

public record InvitationPreviewResponse(
        String email,
        String role,
        String status,
        String expiresAt,
        String tenantName,
        String accessLevelName
) {}
