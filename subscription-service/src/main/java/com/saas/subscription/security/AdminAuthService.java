package com.saas.subscription.security;

import com.saas.subscription.client.AdminPermissionClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Checagem de autorização administrativa. requireAdminPermission delega ao admin-service
 * (fonte única de verdade — ver AdminPermissionClient/AdminPermissionResource) em vez de
 * duplicar a consulta a user_profiles/admin_access_level_permissions localmente.
 */
@ApplicationScoped
public class AdminAuthService {

    @Inject
    JsonWebToken jwt;

    @Inject
    @RestClient
    AdminPermissionClient adminPermissionClient;

    @Context
    HttpHeaders httpHeaders;

    public String currentUserId() {
        String userId = jwt.getSubject();
        if (userId == null) throw new ForbiddenException("Não autenticado");
        return userId;
    }

    /**
     * Exige que o usuário seja SUPER_ADMIN ou ADMIN_USER ativo com a permissão especificada.
     * Passar null em permissionKey verifica apenas que é um admin válido (para listagens gerais).
     */
    public void requireAdminPermission(String permissionKey) {
        String authorization = httpHeaders.getHeaderString(HttpHeaders.AUTHORIZATION);
        try (Response response = adminPermissionClient.checkPermission(authorization, permissionKey)) {
            if (response.getStatus() == Response.Status.NO_CONTENT.getStatusCode()) return;
            throw new ForbiddenException("Você não possui permissão para executar esta ação");
        }
    }
}
