package com.saas.subscription.security;

import com.saas.platformtenant.AbstractTenantResolutionFilter;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ext.Provider;

/**
 * Resolve o tenant ativo (Supabase JWT + X-Tenant-ID) para as rotas tenant-scoped
 * (/api/v1/subscriptions/**). Rotas administrativas (/api/v1/admin/**) e públicas
 * (/api/v1/public/**) não usam TenantContext. Fluxo compartilhado com os demais serviços
 * tenant-scoped via {@link AbstractTenantResolutionFilter} — só a lista de exclusão é própria
 * deste serviço.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION)
public class TenantResolutionFilter extends AbstractTenantResolutionFilter {

    @Override
    protected boolean isExcluded(String path) {
        return path.startsWith("/q/")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/api/v1/admin/");
    }
}
