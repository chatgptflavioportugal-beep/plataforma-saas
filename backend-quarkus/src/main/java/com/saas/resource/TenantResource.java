package com.saas.resource;

import com.saas.repository.UserTenantRepository;
import com.saas.security.TenantContext;
import com.saas.service.TenantService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/api/v1/tenants")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class TenantResource {

    @Inject
    TenantService tenantService;

    @Inject
    UserTenantRepository userTenantRepository;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/mine")
    public Response myTenants() {
        UUID userId = UUID.fromString(jwt.getSubject());
        var rows = userTenantRepository.findAllByUser(userId);
        var result = rows.stream().map(r -> {
            var tenant = new java.util.LinkedHashMap<String, Object>();
            tenant.put("id", r.tenantId());
            tenant.put("name", r.tenantName());
            tenant.put("slug", r.tenantSlug());
            tenant.put("status", r.tenantStatus());
            tenant.put("type", r.tenantType());
            tenant.put("plan_id", r.planId());
            tenant.put("trial_ends_at", r.trialEndsAt());
            tenant.put("created_at", r.createdAt());
            tenant.put("updated_at", r.updatedAt());

            var ut = new java.util.LinkedHashMap<String, Object>();
            ut.put("id", r.id());
            ut.put("user_id", r.userId());
            ut.put("tenant_id", r.tenantId());
            ut.put("role", r.role());
            ut.put("is_active", r.isActive());
            ut.put("tenant", tenant);
            return ut;
        }).toList();
        return Response.ok(result).build();
    }

    @GET
    @Path("/{tenantId}/context")
    public Response tenantContext(@PathParam("tenantId") UUID tenantId,
                                  @Context SecurityContext ctx) {
        TenantContext tenantCtx = TenantContext.from(ctx);
        if (!tenantCtx.getTenantId().equals(tenantId)) {
            return Response.status(403).build();
        }
        return Response.ok(tenantService.getTenantContext(tenantId)).build();
    }
}
