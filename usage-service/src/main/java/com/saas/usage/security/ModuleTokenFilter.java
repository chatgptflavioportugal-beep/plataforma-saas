package com.saas.usage.security;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Map;

/**
 * Todas as rotas do Usage Service (/api/v1/usage/**) exigem um ModuleAccessToken válido,
 * emitido pelo auth-service para o módulo chamador (PDF, WhatsApp, etc.). Nenhuma consulta
 * a outro serviço é feita aqui — tudo vem das claims do próprio token.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION)
public class ModuleTokenFilter implements ContainerRequestFilter {

    @Inject
    TokenService tokenService;

    @Inject
    ModuleTokenContextHolder contextHolder;

    private static final String USAGE_API_PREFIX = "/api/v1/usage/";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (!path.startsWith(USAGE_API_PREFIX) && !("api/v1/usage".equals(path))) {
            return;
        }

        String authHeader = requestContext.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity(Map.of("error", "ModuleAccessToken ausente"))
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
                            .entity(Map.of("error", e.getMessage()))
                            .build()
            );
            return;
        }

        contextHolder.set(new ModuleTokenContext(
                claims.userId(),
                claims.tenantId(),
                claims.moduleId(),
                claims.moduleSlug(),
                claims.permissions(),
                claims.limits()
        ));
    }
}
