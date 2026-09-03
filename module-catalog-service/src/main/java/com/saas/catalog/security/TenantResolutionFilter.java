package com.saas.catalog.security;

import com.saas.platformtenant.AbstractTenantResolutionFilter;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ext.Provider;

/**
 * Resolve o tenant ativo (Supabase JWT + X-Tenant-ID) para as rotas de catálogo consumidas
 * pelo cliente (/api/v1/services/resolve-route/**). As rotas administrativas
 * (/api/v1/admin/**) não usam TenantContext — apenas @Authenticated + checagem de admin
 * (system_role/admin_access_level), então são excluídas aqui. Fluxo compartilhado com os
 * demais serviços tenant-scoped via {@link AbstractTenantResolutionFilter} — só a lista de
 * exclusão é própria deste serviço.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION)
public class TenantResolutionFilter extends AbstractTenantResolutionFilter {

    @Override
    protected boolean isExcluded(String path) {
        return path.startsWith("/q/") || path.startsWith("/api/v1/admin/");
    }
}
