package com.saas.profile.dto.member;

public record SendInvitationResponse(String id, String email, String accessLevelId, String accessLevelName, String expiresAt) {}
