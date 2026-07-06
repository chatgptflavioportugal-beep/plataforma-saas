package com.saas.security;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dados do ModuleAccessToken já validado, disponíveis durante o processamento da requisição.
 * Acessar via ModuleTokenContextHolder (CDI @RequestScoped).
 */
public class ModuleTokenContext {

    private final UUID userId;
    private final UUID tenantId;
    private final String moduleId;
    private final String moduleSlug;
    private final String planName;
    private final String accessSource;
    private final List<String> permissions;
    private final Map<String, Object> limits;
    private final int permissionsVersion;

    public ModuleTokenContext(
            UUID userId,
            UUID tenantId,
            String moduleId,
            String moduleSlug,
            String planName,
            String accessSource,
            List<String> permissions,
            Map<String, Object> limits,
            int permissionsVersion
    ) {
        this.userId             = userId;
        this.tenantId           = tenantId;
        this.moduleId           = moduleId;
        this.moduleSlug         = moduleSlug;
        this.planName           = planName;
        this.accessSource       = accessSource;
        this.permissions        = permissions;
        this.limits             = limits != null ? limits : Map.of();
        this.permissionsVersion = permissionsVersion;
    }

    public UUID getUserId()                        { return userId; }
    public UUID getTenantId()                      { return tenantId; }
    public String getModuleId()                    { return moduleId; }
    public String getModuleSlug()                  { return moduleSlug; }
    public String getPlanName()                    { return planName; }
    public String getAccessSource()                { return accessSource; }
    public List<String> getPermissions()           { return permissions; }
    public Map<String, Object> getLimits()         { return limits; }
    public int getPermissionsVersion()             { return permissionsVersion; }

    public boolean hasPermission(String key) {
        return permissions != null && permissions.contains(key);
    }

    public Object getLimit(String code) {
        return limits != null ? limits.get(code) : null;
    }

    public void requirePermission(String key) {
        if (!hasPermission(key)) {
            throw new jakarta.ws.rs.ForbiddenException(
                "Você não possui permissão para executar esta ação: " + key
            );
        }
    }
}
