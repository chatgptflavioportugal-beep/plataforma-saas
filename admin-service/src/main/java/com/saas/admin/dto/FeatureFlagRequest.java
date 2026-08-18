package com.saas.admin.dto;

public record FeatureFlagRequest(String key, String name, String description, Boolean isEnabled) {
}
