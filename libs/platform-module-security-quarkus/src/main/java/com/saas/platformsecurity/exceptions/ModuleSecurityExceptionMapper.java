package com.saas.platformsecurity.exceptions;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Mapeia qualquer ModuleSecurityException para a resposta HTTP correspondente — cobre não só
 * as requisições abortadas pelo ModuleAccessFilter, mas também chamadas a
 * ModuleContext#requirePermission() feitas de dentro do endpoint (ex.: checagem de permissão
 * condicional à lógica de negócio).
 */
@Provider
public class ModuleSecurityExceptionMapper implements ExceptionMapper<ModuleSecurityException> {

    @Override
    public Response toResponse(ModuleSecurityException exception) {
        return Response.status(exception.getStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", exception.getMessage()))
                .build();
    }
}
