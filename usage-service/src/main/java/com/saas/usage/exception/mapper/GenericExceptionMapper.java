package com.saas.usage.exception.mapper;

import com.saas.usage.dto.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Traduz qualquer exceção não tratada para o contrato padrão ErrorResponse
 * (code, message, details, timestamp, traceId).
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        String traceId = UUID.randomUUID().toString();

        if (exception instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            String code = status == 404 ? "NOT_FOUND"
                    : status == 400 ? "BAD_REQUEST"
                    : status == 401 ? "UNAUTHORIZED"
                    : status == 403 ? "FORBIDDEN"
                    : "REQUEST_ERROR";
            return Response.status(status)
                    .entity(ErrorResponse.of(code, exception.getMessage(), traceId))
                    .build();
        }

        LOG.error("Erro não tratado no usage-service [traceId=" + traceId + "]", exception);
        return Response.status(500)
                .entity(ErrorResponse.of("INTERNAL_ERROR", "Erro interno inesperado", traceId))
                .build();
    }
}
