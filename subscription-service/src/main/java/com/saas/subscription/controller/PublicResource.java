package com.saas.subscription.controller;

import com.saas.subscription.service.PlanService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Endpoints públicos do domínio de planos/assinaturas: catálogo de planos e
 * módulos disponíveis para contratação.
 */
@Path("/api/v1/public")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublicResource {

    @Inject
    PlanService planService;

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of("status", "UP")).build();
    }

    /**
     * Lista planos ativos. Filtro opcional: ?type=individual ou ?type=business.
     * Sem filtro retorna todos os planos ativos.
     */
    @GET
    @Path("/plans")
    public Response listPlans(@QueryParam("type") String planType) {
        return Response.ok(planService.listActivePlans(planType)).build();
    }

    /**
     * Lista módulos ativos com os planos disponíveis para contratação.
     * Usado na tela de contratação orientada por módulos.
     */
    @GET
    @Path("/modules/billing-options")
    public Response listModuleBillingOptions() {
        return Response.ok(planService.listModuleBillingOptions()).build();
    }
}
