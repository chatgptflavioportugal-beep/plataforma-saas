package com.saas.admin.controller;

import com.saas.admin.client.SubscriptionServiceClient;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Ações administrativas sobre o ciclo de vida de assinaturas — cancelar/
 * reativar em nome de um administrador da plataforma.
 *
 * frontend-admin só pode consumir admin-service; profile_module_subscriptions
 * é tabela de propriedade de subscription-service (regra de table ownership),
 * então este recurso apenas repassa a requisição — autenticação e checagem
 * de permissão administrativa continuam sendo feitas por lá, em
 * AdminAuthService, usando o mesmo Authorization repassado aqui.
 */
@Path("/api/v1/admin/subscriptions")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminSubscriptionResource {

    @Inject
    @RestClient
    SubscriptionServiceClient subscriptionServiceClient;

    @POST
    @Path("/{id}/cancel")
    public Response cancel(@PathParam("id") String id, @Context HttpHeaders httpHeaders) {
        String authorization = httpHeaders.getHeaderString(HttpHeaders.AUTHORIZATION);
        try (Response upstream = subscriptionServiceClient.cancelSubscription(authorization, id)) {
            return Response.status(upstream.getStatus()).entity(upstream.readEntity(String.class)).build();
        }
    }

    @POST
    @Path("/{id}/reactivate")
    public Response reactivate(@PathParam("id") String id, @Context HttpHeaders httpHeaders) {
        String authorization = httpHeaders.getHeaderString(HttpHeaders.AUTHORIZATION);
        try (Response upstream = subscriptionServiceClient.reactivateSubscription(authorization, id)) {
            return Response.status(upstream.getStatus()).entity(upstream.readEntity(String.class)).build();
        }
    }
}
