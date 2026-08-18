package com.saas.admin.dto;

public record SystemAdminDTO(
        String id,
        String email,
        String fullName,
        String systemRole,
        Boolean isActive,
        String createdAt) {
}
