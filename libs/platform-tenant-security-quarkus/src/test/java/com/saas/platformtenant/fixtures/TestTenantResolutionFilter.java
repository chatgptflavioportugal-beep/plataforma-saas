package com.saas.platformtenant.fixtures;

import com.saas.platformtenant.AbstractTenantResolutionFilter;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ext.Provider;

@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION)
public class TestTenantResolutionFilter extends AbstractTenantResolutionFilter {

    @Override
    protected boolean isExcluded(String path) {
        return path.contains("test/tenant/excluded");
    }
}
