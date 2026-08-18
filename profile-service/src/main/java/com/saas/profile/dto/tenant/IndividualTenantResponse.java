package com.saas.profile.dto.tenant;

public record IndividualTenantResponse(String id, String name, String slug, String type, boolean alreadyExists) {}
