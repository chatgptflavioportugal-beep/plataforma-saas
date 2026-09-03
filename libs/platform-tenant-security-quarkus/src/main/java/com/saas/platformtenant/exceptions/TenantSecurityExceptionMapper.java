package com.saas.platformtenant.exceptions;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/** Mapeia qualquer TenantSecurityException para a resposta HTTP correspondente, em JSON. */
@Provider
public class TenantSecurityExceptionMapper implements ExceptionMapper<TenantSecurityException> {

    @Override
    public Response toResponse(TenantSecurityException exception) {
        return Response.status(exception.getStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", exception.getMessage()))
                .build();
    }
}
