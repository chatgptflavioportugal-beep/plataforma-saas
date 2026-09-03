package com.saas.auth.security;

import com.saas.platformtenant.AbstractTenantResolutionFilter;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ext.Provider;

/**
 * Resolve o tenant ativo (Supabase JWT + X-Tenant-ID) para as rotas de emissão de token deste
 * serviço (/api/v1/profile/**, /api/v1/module-token/**). Lógica compartilhada em
 * platform-tenant-security-quarkus — este serviço só declara o que fica de fora.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION)
public class TenantResolutionFilter extends AbstractTenantResolutionFilter {

    @Override
    protected boolean isExcluded(String path) {
        return path.startsWith("/q/") || path.startsWith("q/");
    }
}
