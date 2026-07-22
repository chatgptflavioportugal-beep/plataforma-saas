package com.saas.subscription.security;

import java.util.UUID;

public class TenantContext {

    private final UUID userId;
    private final UUID tenantId;
    private final String userRole;
    private final String planCode;
    private final java.util.Set<String> moduleSlugSet;
    private final String subscriptionStatus;

    public TenantContext(UUID userId, UUID tenantId, String userRole,
                         String planCode, java.util.Set<String> moduleSlugSet,
                         String subscriptionStatus) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.userRole = userRole;
        this.planCode = planCode;
        this.moduleSlugSet = moduleSlugSet;
        this.subscriptionStatus = subscriptionStatus;
    }

    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public String getUserRole() { return userRole; }
    public String getPlanCode() { return planCode; }
    public String getSubscriptionStatus() { return subscriptionStatus; }

    public boolean hasFeature(String moduleSlug) {
        return moduleSlugSet.contains(moduleSlug);
    }

    public java.util.Set<String> getModuleSlugSet() { return moduleSlugSet; }

    public static TenantContext from(jakarta.ws.rs.core.SecurityContext ctx) {
        if (ctx.getUserPrincipal() instanceof TenantContextPrincipal p) {
            return p.getTenantContext();
        }
        throw new IllegalStateException("TenantContext not available in SecurityContext");
    }
}
