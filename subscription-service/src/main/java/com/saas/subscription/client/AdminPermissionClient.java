package com.saas.subscription.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Cliente REST para o endpoint de checagem de permissão administrativa do admin-service
 * — fonte única de verdade para requireAdminPermission (ver AdminAuthService).
 */
@RegisterRestClient(configKey = "admin-service-api")
public interface AdminPermissionClient {

    @GET
    @Path("/api/v1/admin/permissions/check")
    Response checkPermission(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                              @QueryParam("permissionKey") String permissionKey);
}
