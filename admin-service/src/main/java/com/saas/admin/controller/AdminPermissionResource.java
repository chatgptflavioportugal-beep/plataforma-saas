package com.saas.admin.controller;

import com.saas.admin.security.AdminAuthService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint interno de checagem de permissão administrativa, consumido pelos demais
 * microsserviços (subscription-service, module-catalog-service, ...) em vez de cada
 * um duplicar a lógica de AdminAuthService.requireAdminPermission. O chamador repassa
 * o mesmo header Authorization recebido do frontend — não há autenticação
 * serviço-a-serviço separada, já que o JWT do Supabase já é verificado aqui do mesmo
 * jeito que em qualquer outro serviço.
 */
@Path("/api/v1/admin/permissions")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AdminPermissionResource {

    @Inject
    AdminAuthService adminAuth;

    /**
     * Retorna 204 se o usuário autenticado é SUPER_ADMIN ou ADMIN_USER ativo com a
     * permissão informada (ou apenas um admin válido, se permissionKey for omitido).
     * Retorna 403 (via GenericExceptionMapper) caso contrário.
     */
    @GET
    @Path("/check")
    public Response check(@QueryParam("permissionKey") String permissionKey) {
        adminAuth.requireAdminPermission(permissionKey);
        return Response.noContent().build();
    }
}
