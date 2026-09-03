package com.saas.platformadmin;

import java.util.UUID;

/** Dados de user_profiles relevantes para a checagem de admin de plataforma. */
public record AdminProfile(String systemRole, boolean isActive, UUID adminAccessLevelId) {
}
