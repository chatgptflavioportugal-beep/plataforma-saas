package com.saas.resource;

import com.saas.entity.Tenant;
import com.saas.service.PlanService;
import com.saas.service.TenantService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
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
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/plans")
    public Response listPlans() {
        return Response.ok(planService.listActivePlans()).build();
    }

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of("status", "UP")).build();
    }

    @POST
    @Path("/onboarding")
    public Response onboarding(Map<String, String> body) {
        if (identity.isAnonymous()) {
            return Response.status(401).entity(Map.of("error", "UNAUTHORIZED")).build();
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        String name = body.get("name");
        String slug = body.get("slug");

        if (name == null || slug == null) {
            return Response.status(400).entity(Map.of("error", "name e slug obrigatórios")).build();
        }

        try {
            Tenant tenant = tenantService.createTenant(name, slug, userId);
            return Response.ok(Map.of("id", tenant.id, "slug", tenant.slug)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(409).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
