package com.saas.profile.security;

import com.saas.profile.repository.TenantSubscriptionRepository;
import com.saas.profile.repository.UserTenantRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.io.IOException;
import java.util.UUID;

/**
 * Resolve o tenant ativo (Supabase JWT + X-Tenant-ID) para as rotas tenant-scoped
 * (/api/v1/tenants/{tenantId}/**). Rotas públicas, /tenants/mine e /invitations/{token}/accept
 * não exigem tenant resolvido (mesma exclusão do TenantResolutionFilter original).
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION)
public class TenantResolutionFilter implements ContainerRequestFilter {

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity identity;

    @Inject
    UserTenantRepository userTenantRepository;

    @Inject
    TenantSubscriptionRepository subscriptionRepository;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        if (path.startsWith("/q/")
                || path.startsWith("/api/v1/public/")
                || path.equals("/api/v1/tenants/mine")
                || path.startsWith("/api/v1/invitations/")) {
            return;
        }

        if (identity.isAnonymous()) {
            return;
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        String tenantIdHeader = requestContext.getHeaderString("X-Tenant-ID");

        var userTenant = resolveUserTenant(userId, tenantIdHeader);

        var subscription = subscriptionRepository.findActiveByTenant(userTenant.tenantId());

        var tenantCtx = new TenantContext(
                userId,
                userTenant.tenantId(),
                userTenant.role(),
                subscription.map(TenantSubscriptionRepository.SubscriptionResult::planCode).orElse(null),
                subscription.map(TenantSubscriptionRepository.SubscriptionResult::moduleSlugSet).orElse(java.util.Set.of()),
                subscription.map(TenantSubscriptionRepository.SubscriptionResult::status).orElse(null)
        );

        var principal = new TenantContextPrincipal(tenantCtx);
        requestContext.setSecurityContext(new SecurityContext() {
            @Override public java.security.Principal getUserPrincipal() { return principal; }
            @Override public boolean isUserInRole(String role) { return userTenant.role().equals(role); }
            @Override public boolean isSecure() { return requestContext.getSecurityContext().isSecure(); }
            @Override public String getAuthenticationScheme() { return "Bearer"; }
        });
    }

    private UserTenantRepository.UserTenantResult resolveUserTenant(UUID userId, String tenantIdHeader) {
        if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
            UUID tenantId = UUID.fromString(tenantIdHeader);
            return userTenantRepository.findByUserAndTenant(userId, tenantId)
                    .orElseThrow(() -> new jakarta.ws.rs.NotAuthorizedException("Acesso negado ao tenant"));
        }
        return userTenantRepository.findDefaultTenant(userId)
                .orElseThrow(() -> new jakarta.ws.rs.NotAuthorizedException("Usuário sem tenant"));
    }
}
