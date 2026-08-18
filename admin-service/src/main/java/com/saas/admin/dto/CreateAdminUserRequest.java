package com.saas.admin.dto;

public record CreateAdminUserRequest(
    String email,
    String fullName,
    String accessLevelId,
    String tempPassword,
    Boolean sendPasswordEmail
) {}
