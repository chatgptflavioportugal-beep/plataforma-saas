package com.saas.admin.dto;

public record FeatureFlagDTO(
        String id,
        String key,
        String name,
        String description,
        Boolean isEnabled,
        String createdAt,
        String updatedAt) {
}
