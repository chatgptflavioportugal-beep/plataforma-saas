package com.saas.resource;

import com.saas.entity.Tenant;
import com.saas.service.PlanService;
import com.saas.service.TenantService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Map;
import java.util.UUID;

@Path("/api/v1/public")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublicResource {

    @Inject
    PlanService planService;

    @Inject
    TenantService tenantService;

    @Inject
    JsonWebToken jwt;

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
     * Onboarding: cria um tenant do tipo BUSINESS.
     * Requer usuário autenticado — não exige tenant ativo (sem X-Tenant-ID).
     */
    @POST
    @Path("/onboarding")
    @Authenticated
    public Response onboarding(Map<String, String> body) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String name = body.get("name");
        String slug = body.get("slug");

        if (name == null || slug == null) {
            return Response.status(400).entity(Map.of("error", "name e slug obrigatórios")).build();
        }

        try {
            Tenant tenant = tenantService.createTenant(name, slug, userId, "business");
            return Response.ok(Map.of("id", tenant.id, "slug", tenant.slug, "type", "business")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(409).entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * Cria (ou retorna) o tenant individual do usuário autenticado.
     * Idempotente — seguro chamar múltiplas vezes.
     * Requer usuário autenticado — não exige tenant ativo (sem X-Tenant-ID).
     */
    @POST
    @Path("/individual-tenant")
    @Authenticated
    public Response createIndividualTenant() {
        UUID userId = UUID.fromString(jwt.getSubject());
        try {
            Map<String, Object> result = tenantService.ensureIndividualTenant(userId);
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
