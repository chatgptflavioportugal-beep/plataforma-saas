package com.saas.security;

import com.saas.repository.UserTenantRepository;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Intercepta requisições às APIs de módulo (/api/v1/modules/{slug}/...) e valida o ModuleAccessToken.
 *
 * Paths excluídos (usam TenantResolutionFilter com Supabase JWT):
 *   POST /api/v1/modules/{slug}/access-token  → geração do token
 *
 * Para todos os outros paths /api/v1/modules/:
 *   - extrai Bearer token do header Authorization
 *   - valida via TokenService (HMAC-SHA256, secret do módulo)
 *   - valida que tokenType == MODULE_ACCESS e moduleSlug bate com o path
 *   - popula ModuleTokenContextHolder para uso pelos Resources
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION)
public class ModuleTokenFilter implements ContainerRequestFilter {

    @Inject
    TokenService tokenService;

    @Inject
    ModuleTokenContextHolder contextHolder;

    @Inject
    UserTenantRepository userTenantRepository;

    private static final String MODULE_API_PREFIX = "/api/v1/modules/";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        // Só processa paths de APIs de módulo
        if (!path.startsWith(MODULE_API_PREFIX)) {
            return;
        }

        // Endpoint de geração de token usa TenantResolutionFilter (Supabase JWT), não este filtro
        if (path.endsWith("/access-token")) {
            return;
        }

        String authHeader = requestContext.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("error", "ModuleAccessToken ausente"))
                    .build()
            );
            return;
        }

        String token = authHeader.substring(7);

        TokenService.ModuleTokenClaims claims;
        try {
            claims = tokenService.validateModuleToken(token);
        } catch (TokenService.TokenException e) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("error", e.getMessage()))
                    .build()
            );
            return;
        }

        // Revalida contra o banco a cada requisição: permite invalidar o token em cache
        // no frontend imediatamente após mudança de nível de acesso, permissões ou
        // assinatura — sem esperar a expiração natural do MAT (30min).
        var permStatus = userTenantRepository.findPermissionsStatus(claims.userId(), claims.tenantId());
        if (permStatus.isEmpty() || !permStatus.get().active()) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("code", "MEMBER_INACTIVE", "error", "Acesso revogado para este perfil"))
                    .build()
            );
            return;
        }
        if (permStatus.get().permissionsVersion() != claims.permissionsVersion()) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("code", "PERMISSIONS_VERSION_MISMATCH", "error", "Permissões alteradas — token expirado"))
                    .build()
            );
            return;
        }

        // Valida que o moduleSlug do token bate com o do path: /api/v1/modules/{slug}/...
        String pathSlug = extractModuleSlug(path);
        if (pathSlug != null && !pathSlug.equals(claims.moduleSlug())) {
            requestContext.abortWith(
                Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("error", "ModuleAccessToken não é válido para este módulo"))
                    .build()
            );
            return;
        }

        contextHolder.set(new ModuleTokenContext(
                claims.userId(),
                claims.tenantId(),
                claims.moduleId(),
                claims.moduleSlug(),
                claims.planName(),
                claims.accessSource(),
                claims.permissions(),
                claims.limits(),
                claims.permissionsVersion()
        ));
    }

    /** Extrai o slug do módulo do path: /api/v1/modules/{slug}/... → slug */
    private String extractModuleSlug(String path) {
        String after = path.substring(MODULE_API_PREFIX.length());
        int slash = after.indexOf('/');
        return slash > 0 ? after.substring(0, slash) : (after.isBlank() ? null : after);
    }
}
