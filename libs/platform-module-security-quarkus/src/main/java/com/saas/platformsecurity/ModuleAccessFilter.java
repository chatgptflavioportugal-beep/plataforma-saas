package com.saas.platformsecurity;

import com.saas.platformsecurity.exceptions.ModuleSecurityException;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Intercepta toda requisição a um endpoint anotado com {@link RequireModuleAccess}: valida o
 * ModuleAccessToken, confere o moduleSlug e, se uma permissão foi declarada, confere que o
 * token a possui. Em caso de sucesso, disponibiliza o {@link ModuleContext} resultante via
 * {@link CurrentModuleContext}. Nenhuma rota precisa repetir esse fluxo manualmente.
 */
@Provider
@RequireModuleAccess(moduleSlug = "")
@Priority(Priorities.AUTHORIZATION)
@ApplicationScoped
public class ModuleAccessFilter implements ContainerRequestFilter {

    @Inject
    ModuleAccessTokenService tokenService;

    @Inject
    CurrentModuleContext currentModuleContext;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        RequireModuleAccess binding = resolveBinding();
        if (binding == null) {
            // Não deveria acontecer (o @NameBinding só invoca este filtro em recursos
            // anotados), mas evita NPE em caso de uso indevido/reflexão inesperada.
            abort(requestContext, 500, "RequireModuleAccess mal configurado.");
            return;
        }

        String authHeader = requestContext.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            abort(requestContext, 401, "Token de acesso do módulo não informado.");
            return;
        }

        String token = authHeader.substring("Bearer ".length());
        ModuleContext context;
        try {
            context = tokenService.validate(token, binding.moduleSlug());
        } catch (ModuleSecurityException e) {
            abort(requestContext, e.getStatus(), e.getMessage());
            return;
        }

        String permission = binding.permission();
        if (!permission.isBlank() && !context.hasPermission(permission)) {
            abort(requestContext, 403,
                    "Você não possui permissão para executar esta ação: " + permission);
            return;
        }

        currentModuleContext.set(context);
    }

    private RequireModuleAccess resolveBinding() {
        Method method = resourceInfo.getResourceMethod();
        RequireModuleAccess binding = method != null
                ? method.getAnnotation(RequireModuleAccess.class)
                : null;
        if (binding == null && resourceInfo.getResourceClass() != null) {
            binding = resourceInfo.getResourceClass().getAnnotation(RequireModuleAccess.class);
        }
        return binding;
    }

    private void abort(ContainerRequestContext ctx, int status, String message) {
        ctx.abortWith(
                Response.status(status)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", message))
                        .build());
    }
}
