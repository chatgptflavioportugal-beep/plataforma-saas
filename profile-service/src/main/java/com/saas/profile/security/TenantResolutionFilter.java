package com.saas.profile.security;

import com.saas.platformtenant.AbstractTenantResolutionFilter;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ext.Provider;

/**
 * Resolve o tenant ativo (Supabase JWT + X-Tenant-ID) para as rotas tenant-scoped
 * (/api/v1/tenants/{tenantId}/**). Rotas públicas, /tenants/mine e /invitations/{token}/accept
 * não exigem tenant resolvido. Fluxo compartilhado com os demais serviços tenant-scoped via
 * {@link AbstractTenantResolutionFilter} — só a lista de exclusão é própria deste serviço.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION)
public class TenantResolutionFilter extends AbstractTenantResolutionFilter {

    @Override
    protected boolean isExcluded(String path) {
        return path.startsWith("/q/")
                || path.startsWith("/api/v1/public/")
                || path.equals("/api/v1/tenants/mine")
                || path.startsWith("/api/v1/invitations/");
    }
}
