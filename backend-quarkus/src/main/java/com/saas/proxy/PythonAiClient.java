package com.saas.proxy;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "python-ai")
@Path("/")
public interface PythonAiClient {

    @POST
    @Path("/pdf/merge")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response mergePdf(
            @HeaderParam("X-Internal-Token") String internalToken,
            @HeaderParam("X-Tenant-ID") String tenantId,
            @HeaderParam("X-User-ID") String userId,
            Object multipartBody
    );
}
