package com.saas.platformtenant;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Resolve o tenant ativo (Supabase JWT + X-Tenant-ID) para rotas tenant-scoped. Cada serviço
 * consumidor registra uma subclasse concreta ({@code @Provider @ApplicationScoped
 * @Priority(Priorities.AUTHORIZATION)}) que só precisa implementar {@link #isExcluded(String)}
 * com a lista de paths públicos/administrativos do serviço — todo o resto do fluxo (ler o JWT,
 * resolver o vínculo com o tenant, buscar a assinatura ativa, montar o SecurityContext) é
 * compartilhado.
 */
public abstract class AbstractTenantResolutionFilter implements ContainerRequestFilter {

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity identity;

    @Inject
    TenantMembershipResolver membershipResolver;

    @Inject
    TenantSubscriptionResolver subscriptionResolver;

    /** true se a rota não exige TenantContext (pública, administrativa, etc). */
    protected abstract boolean isExcluded(String path);

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        if (isExcluded(path)) {
            return;
        }

        if (identity.isAnonymous()) {
            return;
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        String tenantIdHeader = requestContext.getHeaderString("X-Tenant-ID");

        TenantMembership membership = resolveMembership(userId, tenantIdHeader);

        var subscription = subscriptionResolver.findActiveByTenant(membership.tenantId());

        var tenantCtx = new TenantContext(
                userId,
                membership.tenantId(),
                membership.role(),
                subscription.map(TenantSubscriptionInfo::planCode).orElse(null),
                subscription.map(TenantSubscriptionInfo::moduleSlugSet).orElse(Set.of()),
                subscription.map(TenantSubscriptionInfo::status).orElse(null)
        );

        var principal = new TenantContextPrincipal(tenantCtx);
        requestContext.setSecurityContext(new SecurityContext() {
            @Override public java.security.Principal getUserPrincipal() { return principal; }
            @Override public boolean isUserInRole(String role) { return membership.role().equals(role); }
            @Override public boolean isSecure() { return requestContext.getSecurityContext().isSecure(); }
            @Override public String getAuthenticationScheme() { return "Bearer"; }
        });
    }

    private TenantMembership resolveMembership(UUID userId, String tenantIdHeader) {
        if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
            UUID tenantId = UUID.fromString(tenantIdHeader);
            return membershipResolver.findByUserAndTenant(userId, tenantId)
                    .orElseThrow(() -> new NotAuthorizedException("Acesso negado ao tenant"));
        }
        return membershipResolver.findDefaultTenant(userId)
                .orElseThrow(() -> new NotAuthorizedException("Usuário sem tenant"));
    }
}
