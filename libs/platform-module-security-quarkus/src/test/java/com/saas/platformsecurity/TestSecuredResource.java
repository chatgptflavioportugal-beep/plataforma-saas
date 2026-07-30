package com.saas.platformsecurity;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/** Recurso de teste apenas — exercita o ModuleAccessFilter/@RequireModuleAccess ponta a ponta. */
@Path("/test/secured")
public class TestSecuredResource {

    @Inject
    CurrentModuleContext current;

    @GET
    @Path("/pdf")
    @Produces(MediaType.APPLICATION_JSON)
    @RequireModuleAccess(moduleSlug = "pdf")
    public Map<String, String> pdf() {
        return Map.of("tenantId", current.get().tenantId().toString());
    }

    @GET
    @Path("/pdf-merge")
    @Produces(MediaType.APPLICATION_JSON)
    @RequireModuleAccess(moduleSlug = "pdf", permission = "pdf-merge")
    public Map<String, Boolean> pdfMerge() {
        return Map.of("ok", true);
    }
}
