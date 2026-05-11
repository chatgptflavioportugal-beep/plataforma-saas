package com.saas.resource;

import com.saas.entity.PdfJob;
import com.saas.security.RequiresPlanFeature;
import com.saas.security.TenantContext;
import com.saas.service.PdfService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Path("/api/v1/pdf")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class PdfResource {

    @Inject
    PdfService pdfService;

    @GET
    @Path("/jobs")
    @RequiresPlanFeature("pdf.merge")
    public Response listJobs(@Context SecurityContext ctx) {
        TenantContext tenantCtx = TenantContext.from(ctx);
        List<PdfJob> jobs = pdfService.listJobs(tenantCtx.getTenantId());
        return Response.ok(jobs).build();
    }

    @POST
    @Path("/merge")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiresPlanFeature("pdf.merge")
    public Response merge(
            @RestForm("file_a") FileUpload fileA,
            @RestForm("file_b") FileUpload fileB,
            @Context SecurityContext ctx
    ) {
        TenantContext tenantCtx = TenantContext.from(ctx);

        if (fileA == null || fileB == null) {
            return Response.status(400)
                    .entity(java.util.Map.of("error", "file_a e file_b obrigatórios"))
                    .build();
        }

        try {
            PdfJob job = pdfService.submitMerge(tenantCtx, fileA, fileB);
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
    @RequiresPlanFeature("pdf.merge")
    public Response download(@PathParam("jobId") UUID jobId, @Context SecurityContext ctx) {
        TenantContext tenantCtx = TenantContext.from(ctx);

        try {
            byte[] bytes = pdfService.downloadResult(jobId, tenantCtx.getTenantId());
            return Response.ok(bytes)
                    .header("Content-Disposition", "attachment; filename=merged-" + jobId + ".pdf")
                    .build();
        } catch (IOException e) {
            return Response.serverError().build();
        }
    }
}
