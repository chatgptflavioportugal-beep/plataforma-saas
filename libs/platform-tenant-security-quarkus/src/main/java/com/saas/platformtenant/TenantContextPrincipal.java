package com.saas.platformtenant;

import java.security.Principal;

public class TenantContextPrincipal implements Principal {

    private final TenantContext tenantContext;

    public TenantContextPrincipal(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public String getName() {
        return tenantContext.getUserId().toString();
    }

    public TenantContext getTenantContext() {
        return tenantContext;
    }
}
