package com.saas.resource;

import com.saas.entity.PdfJob;
import com.saas.security.ModuleTokenContext;
import com.saas.security.ModuleTokenContextHolder;
import com.saas.service.PdfService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.Set;

/**
 * APIs do módulo PDF autenticadas via ModuleAccessToken.
 *
 * Path base: /api/v1/modules/pdf/
 * Autenticação: ModuleTokenFilter (não usa TenantResolutionFilter nem Supabase JWT).
 *
 * Cada endpoint valida a permissão específica do serviço antes de executar.
 */
@Path("/api/v1/modules/pdf")
@Produces(MediaType.APPLICATION_JSON)
public class PdfModuleResource {

    private static final String PERM_MERGE  = "module.pdf.merge-pdf";
    private static final String PERM_JOBS   = "module.pdf.merge-pdf";

    @Inject
    ModuleTokenContextHolder tokenContextHolder;

    @Inject
    PdfService pdfService;

    @GET
    @Path("/jobs")
    public Response listJobs() {
        ModuleTokenContext ctx = tokenContextHolder.get();
        ctx.requirePermission(PERM_JOBS);

        List<PdfJob> jobs = pdfService.listJobs(ctx.getTenantId());
        return Response.ok(jobs).build();
    }

    @POST
    @Path("/merge")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response merge(
            @HeaderParam("Authorization") String authorizationHeader,
            @RestForm("file_a") FileUpload fileA,
            @RestForm("file_b") FileUpload fileB
    ) {
        ModuleTokenContext ctx = tokenContextHolder.get();
        ctx.requirePermission(PERM_MERGE);

        if (fileA == null || fileB == null) {
            return Response.status(400)
                    .entity(java.util.Map.of("error", "file_a e file_b obrigatórios"))
                    .build();
        }

        String moduleToken = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : null;

        var tenantCtx = new com.saas.security.TenantContext(
                ctx.getUserId(),
                ctx.getTenantId(),
                "member",
                ctx.getPlanName(),
                Set.of(ctx.getModuleSlug()),
                "active"
        );

        try {
            PdfJob job = pdfService.submitMerge(tenantCtx, fileA, fileB, moduleToken);
            return Response.ok(job).build();
        } catch (IOException e) {
            return Response.serverError()
                    .entity(java.util.Map.of("error", "Erro ao processar arquivo"))
                    .build();
        }
    }

    @GET
    @Path("/jobs/{jobId}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam("jobId") UUID jobId) {
        ModuleTokenContext ctx = tokenContextHolder.get();
        ctx.requirePermission(PERM_JOBS);

        try {
            byte[] bytes = pdfService.downloadResult(jobId, ctx.getTenantId());
            return Response.ok(bytes)
                    .header("Content-Disposition", "attachment; filename=merged-" + jobId + ".pdf")
                    .build();
        } catch (IOException e) {
            return Response.serverError().build();
        }
    }
}
