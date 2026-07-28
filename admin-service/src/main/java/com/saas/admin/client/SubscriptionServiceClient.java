package com.saas.admin.client;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Cliente REST para as ações administrativas de assinatura do subscription-
 * service (cancelar/reativar) — profile_module_subscriptions pertence àquele
 * serviço, então admin-service só encaminha a requisição, repassando o mesmo
 * header Authorization recebido do frontend (mesmo padrão de
 * com.saas.admin.controller.AdminPermissionResource, na direção oposta).
 */
@RegisterRestClient(configKey = "subscription-service-api")
public interface SubscriptionServiceClient {

    @POST
    @Path("/api/v1/admin/subscriptions/{id}/cancel")
    Response cancelSubscription(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                                 @PathParam("id") String id);

    @POST
    @Path("/api/v1/admin/subscriptions/{id}/reactivate")
    Response reactivateSubscription(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                                     @PathParam("id") String id);
}
