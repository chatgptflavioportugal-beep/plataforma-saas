package com.saas.payment.exception.mapper;

import com.saas.payment.dto.response.ErrorResponse;
import com.saas.payment.exception.PaymentNotFoundException;
import com.saas.payment.exception.PaymentUnavailableException;
import com.saas.payment.exception.PaymentValidationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Traduz qualquer exceção não tratada para o contrato padrão ErrorResponse
 * (code, message, details, timestamp, traceId). Nunca loga API keys, tokens
 * ou payload bruto de gateway — apenas a mensagem da exceção.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        String traceId = UUID.randomUUID().toString();

        if (exception instanceof PaymentValidationException) {
            return build(400, "PAYMENT_VALIDATION_ERROR", exception, traceId);
        }
        if (exception instanceof PaymentNotFoundException) {
            return build(404, "PAYMENT_NOT_FOUND", exception, traceId);
        }
        if (exception instanceof PaymentUnavailableException) {
            return build(503, "PAYMENT_GATEWAY_UNAVAILABLE", exception, traceId);
        }

        if (exception instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            String code = status == 404 ? "NOT_FOUND"
                    : status == 400 ? "BAD_REQUEST"
                    : status == 401 ? "UNAUTHORIZED"
                    : status == 403 ? "FORBIDDEN"
                    : "REQUEST_ERROR";
            return build(status, code, exception, traceId);
        }

        LOG.error("Erro não tratado no payment-service [traceId=" + traceId + "]", exception);
        return Response.status(500)
                .entity(ErrorResponse.of("INTERNAL_ERROR", "Erro interno inesperado", traceId))
                .build();
    }

    private Response build(int status, String code, Exception exception, String traceId) {
        if (status >= 500) {
            LOG.error("Erro no payment-service [traceId=" + traceId + "]", exception);
        } else {
            LOG.warnf("Requisição rejeitada [traceId=%s, code=%s]: %s", traceId, code, exception.getMessage());
        }
        return Response.status(status)
                .entity(ErrorResponse.of(code, exception.getMessage(), traceId))
                .build();
    }
}
