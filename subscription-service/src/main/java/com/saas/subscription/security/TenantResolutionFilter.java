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
 *
 * /api/v1/internal/payments/** também é excluída: é chamada pelo payment-service
 * autenticada por segredo compartilhado (X-Internal-Token), sem nenhum JWT de
 * usuário para este filtro resolver (ver InternalPaymentResource). Diferente de
 * /api/v1/internal/module-access/**, que passa pelo filtro normalmente porque
 * repassa o Authorization do usuário.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION)
public class TenantResolutionFilter extends AbstractTenantResolutionFilter {

    @Override
    protected boolean isExcluded(String path) {
        return path.startsWith("/q/")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/api/v1/admin/")
                || path.startsWith("/api/v1/internal/payments/");
    }
}
