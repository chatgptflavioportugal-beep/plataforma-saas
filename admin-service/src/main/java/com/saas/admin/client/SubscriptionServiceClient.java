package com.saas.admin.client;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Cliente REST para ações administrativas sobre assinaturas — profile_module_
 * subscriptions é tabela de propriedade do subscription-service (ver regra de
 * table ownership), então o admin-service não escreve nela diretamente e
 * delega para o endpoint /api/v1/admin/subscriptions daquele serviço.
 * Repassa o mesmo Authorization (JWT Supabase) recebido do frontend-admin;
 * subscription-service valida a permissão administrativa localmente via
 * AdminAuthService.
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
