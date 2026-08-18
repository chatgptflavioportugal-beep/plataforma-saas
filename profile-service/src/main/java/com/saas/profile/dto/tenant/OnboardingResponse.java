package com.saas.profile.dto.tenant;

import java.util.UUID;

public record OnboardingResponse(UUID id, String slug, String type) {}
