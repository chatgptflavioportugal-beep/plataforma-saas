package com.saas.usage.security;

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
    private final List<String> permissions;
    private final Map<String, Object> limits;

    public ModuleTokenContext(
            UUID userId,
            UUID tenantId,
            String moduleId,
            String moduleSlug,
            List<String> permissions,
            Map<String, Object> limits
    ) {
        this.userId      = userId;
        this.tenantId    = tenantId;
        this.moduleId    = moduleId;
        this.moduleSlug  = moduleSlug;
        this.permissions = permissions;
        this.limits      = limits != null ? limits : Map.of();
    }

    public UUID getUserId()                { return userId; }
    public UUID getTenantId()               { return tenantId; }
    public String getModuleId()             { return moduleId; }
    public String getModuleSlug()           { return moduleSlug; }
    public List<String> getPermissions()    { return permissions; }
    public Map<String, Object> getLimits()  { return limits; }

    public Object getLimit(String code) {
        return limits != null ? limits.get(code) : null;
    }
}
