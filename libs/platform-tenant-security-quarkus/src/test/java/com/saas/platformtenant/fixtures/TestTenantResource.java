package com.saas.platformtenant.fixtures;

import com.saas.platformtenant.TenantContext;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Recurso de teste apenas — exercita AbstractTenantResolutionFilter/TenantContext ponta a ponta. */
@Path("/test/tenant")
public class TestTenantResource {

    @GET
    @Path("/secured")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> secured(@Context SecurityContext securityContext) {
        TenantContext ctx = TenantContext.from(securityContext);
        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", ctx.getTenantId().toString());
        body.put("planCode", ctx.getPlanCode() == null ? "" : ctx.getPlanCode());
        body.put("hasPdf", ctx.hasFeature("pdf"));
        return body;
    }

    @GET
    @Path("/excluded")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Boolean> excluded() {
        return Map.of("ok", true);
    }

    /** Autenticado, mas em path excluído: prova que isExcluded() pula a resolução de tenant
     *  mesmo para um usuário sem nenhum vínculo (que falharia se o filtro rodasse de verdade). */
    @GET
    @Path("/excluded-authenticated")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Boolean> excludedAuthenticated() {
        return Map.of("ok", true);
    }

    @GET
    @Path("/{tenantId}/scoped")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> scoped(@PathParam("tenantId") UUID tenantId, @Context SecurityContext securityContext) {
        TenantContext ctx = TenantContext.resolveAndCheck(securityContext, tenantId);
        return Map.of("tenantId", ctx.getTenantId().toString());
    }
}
