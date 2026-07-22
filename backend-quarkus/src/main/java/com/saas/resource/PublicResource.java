package com.saas.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Health check público do backend-quarkus.
 *
 * Onboarding, criação de tenant individual e preview de convite foram movidos para
 * o profile-service (Fase 3). Listagem de planos e módulos de billing foram movidos
 * para o subscription-service (Fase 4) — ver com.saas.subscription.controller.PublicResource lá.
 */
@Path("/api/v1/public")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublicResource {

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of("status", "UP")).build();
    }
}
