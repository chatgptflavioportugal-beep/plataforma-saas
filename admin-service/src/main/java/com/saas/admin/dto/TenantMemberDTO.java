package com.saas.admin.dto;

public record TenantMemberDTO(String fullName, String email, String role, Boolean isActive, String joinedAt) {
}
