package com.saas.admin.dto;

public record TenantInvitationDTO(String email, String role, String createdAt, String invitedByName) {
}
