package com.saas.profile.dto.member;

public record MemberDto(
        String userId,
        String fullName,
        String email,
        String role,
        String joinedAt,
        String accessLevelId,
        String accessLevelName
) {}
