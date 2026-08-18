package com.saas.profile.dto.member;

public record InvitationDto(
        String id,
        String email,
        String role,
        String status,
        String expiresAt,
        String createdAt,
        String accessLevelId,
        String accessLevelName
) {}
