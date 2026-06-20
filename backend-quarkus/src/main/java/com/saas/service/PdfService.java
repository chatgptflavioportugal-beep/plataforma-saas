package com.saas.service;

import com.saas.entity.PdfJob;
import com.saas.proxy.PythonAiClient;
import com.saas.security.TenantContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PdfService {

    @RestClient
    PythonAiClient pythonAiClient;

    @Inject
    AuditService auditService;

    @ConfigProperty(name = "python.ai.internal-token")
    String internalToken;

    @ConfigProperty(name = "pdf.storage.path", defaultValue = "/tmp/saas-pdf")
    String storagePath;

    public List<PdfJob> listJobs(UUID tenantId) {
        return PdfJob.findByTenant(tenantId);
    }

    @Transactional
    public PdfJob submitMerge(TenantContext ctx, FileUpload fileA, FileUpload fileB, String moduleToken) throws IOException {
        Path storageDir = Paths.get(storagePath, ctx.getTenantId().toString());
        Files.createDirectories(storageDir);

        String jobId = UUID.randomUUID().toString();
        Path pathA = storageDir.resolve(jobId + "-a.pdf");
        Path pathB = storageDir.resolve(jobId + "-b.pdf");

        Files.copy(fileA.uploadedFile(), pathA);
        Files.copy(fileB.uploadedFile(), pathB);

        PdfJob job = new PdfJob();
        job.tenantId = ctx.getTenantId();
        job.userId = ctx.getUserId();
        job.status = "pending";
        job.fileAName = fileA.fileName();
        job.fileBName = fileB.fileName();
        job.fileAPath = pathA.toString();
        job.fileBPath = pathB.toString();
        job.persist();

        auditService.log(ctx, "pdf.merge.submitted", "pdf_jobs", job.id.toString(), null, null);

        processMergeAsync(job.id, moduleToken);

        return job;
    }

    @Transactional
    void processMergeAsync(UUID jobId, String moduleToken) {
        PdfJob job = PdfJob.findById(jobId);
        if (job == null) return;

        job.status = "processing";
        job.persist();

        String authorization = moduleToken != null ? "Bearer " + moduleToken : "Bearer " + internalToken;

        try {
            var form = new com.saas.proxy.PdfMergeForm();
            form.fileA = new java.io.File(job.fileAPath);
            form.fileB = new java.io.File(job.fileBPath);
            Response response = pythonAiClient.mergePdf(
                    authorization,
                    job.tenantId.toString(),
                    job.userId.toString(),
                    form
            );

            if (response.getStatus() == 200) {
                byte[] resultBytes = response.readEntity(byte[].class);
                Path resultPath = Paths.get(storagePath, job.tenantId.toString(),
                        job.id.toString() + "-merged.pdf");
                Files.write(resultPath, resultBytes);

                job.status = "completed";
                job.resultPath = resultPath.toString();
                job.resultName = "merged-" + job.id + ".pdf";
            } else {
                job.status = "failed";
                job.errorMessage = "Erro no serviço Python: " + response.getStatus();
            }
        } catch (Exception e) {
            job.status = "failed";
            job.errorMessage = e.getMessage();
        }

        job.persist();
    }

    public byte[] downloadResult(UUID jobId, UUID tenantId) throws IOException {
        PdfJob job = PdfJob.findByIdAndTenant(jobId, tenantId)
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("Job não encontrado"));

        if (!"completed".equals(job.status)) {
            throw new IllegalStateException("Job não concluído");
        }

        return Files.readAllBytes(Paths.get(job.resultPath));
    }

}
