package com.saas.platformsecurity;

import com.saas.platformsecurity.exceptions.PermissionDeniedException;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Claims já validadas do ModuleAccessToken, disponíveis para o endpoint via
 * {@link CurrentModuleContext}. Equivalente ao ModuleContext da biblioteca Python
 * platform-module-security-python — mesmos campos, mesmos helpers.
 */
public record ModuleContext(
        UUID userId,
        UUID tenantId,
        String moduleId,
        String moduleSlug,
        String planName,
        String accessSource,
        List<String> permissions,
        Map<String, Object> limits,
        int permissionsVersion,
        Date issuedAt,
        Date expiresAt
) {

    public boolean hasPermission(String key) {
        return permissions != null && permissions.contains(key);
    }

    public void requirePermission(String key) {
        if (!hasPermission(key)) {
            throw new PermissionDeniedException(
                    "Você não possui permissão para executar esta ação: " + key);
        }
    }

    public Object getLimit(String code) {
        return getLimit(code, null);
    }

    public Object getLimit(String code, Object defaultValue) {
        return limits != null ? limits.getOrDefault(code, defaultValue) : defaultValue;
    }
}
