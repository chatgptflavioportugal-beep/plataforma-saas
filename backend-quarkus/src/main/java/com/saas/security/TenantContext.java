package com.saas.security;

import java.util.UUID;

public class TenantContext {

    private final UUID userId;
    private final UUID tenantId;
    private final String userRole;
    private final String planCode;
    private final java.util.Map<String, Object> planFeatures;
    private final String subscriptionStatus;

    public TenantContext(UUID userId, UUID tenantId, String userRole,
                         String planCode, java.util.Map<String, Object> planFeatures,
                         String subscriptionStatus) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.userRole = userRole;
        this.planCode = planCode;
        this.planFeatures = planFeatures;
        this.subscriptionStatus = subscriptionStatus;
    }

    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public String getUserRole() { return userRole; }
    public String getPlanCode() { return planCode; }
    public String getSubscriptionStatus() { return subscriptionStatus; }

    public boolean hasFeature(String featureKey) {
        Object value = planFeatures.get(featureKey);
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return false;
    }

    public int getFeatureLimit(String featureKey) {
        Object value = planFeatures.get(featureKey);
        if (value instanceof Number n) return n.intValue();
        return 0;
    }

    public static TenantContext from(jakarta.ws.rs.core.SecurityContext ctx) {
        if (ctx.getUserPrincipal() instanceof TenantContextPrincipal p) {
            return p.getTenantContext();
        }
        throw new IllegalStateException("TenantContext not available in SecurityContext");
    }
}
