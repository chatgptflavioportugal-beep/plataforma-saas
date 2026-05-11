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
        var tenants = userTenantRepository.findDefaultTenant(userId);
        return Response.ok(tenants).build();
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
