package com.saas.usage.security;

import com.saas.platformsecurity.CurrentModuleContext;
import com.saas.platformsecurity.ModuleAccessTokenService;
import com.saas.platformsecurity.ModuleContext;
import com.saas.platformsecurity.exceptions.ModuleSecurityException;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Todas as rotas do Usage Service (/api/v1/usage/**) exigem um ModuleAccessToken válido,
 * emitido pelo auth-service para o módulo chamador (PDF, WhatsApp, etc.). Aceita tokens de
 * qualquer módulo (passa {@code null} como moduleSlug esperado) — usage-service é consumido
 * por todos os módulos, não é exclusivo de um. Validação delegada a
 * platform-module-security-quarkus; nenhuma consulta a outro serviço é feita aqui.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.AUTHORIZATION)
public class ModuleTokenFilter implements ContainerRequestFilter {

    @Inject
    ModuleAccessTokenService tokenService;

    @Inject
    CurrentModuleContext currentModuleContext;

    private static final String USAGE_API_PREFIX = "/api/v1/usage/";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (!path.startsWith(USAGE_API_PREFIX) && !"api/v1/usage".equals(path)) {
            return;
        }

        String authHeader = requestContext.getHeaderString("Authorization");
        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring("Bearer ".length())
                : null;

        ModuleContext context;
        try {
            context = tokenService.validate(token, null);
        } catch (ModuleSecurityException e) {
            requestContext.abortWith(
                    Response.status(e.getStatus())
                            .type(MediaType.APPLICATION_JSON)
                            .entity(Map.of("error", e.getMessage()))
                            .build());
            return;
        }

        currentModuleContext.set(context);
    }
}
