package com.saas.subscription.exception;

import com.saas.subscription.dto.response.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Padroniza o corpo de erro de toda a API neste serviço: {"error": "<mensagem>"},
 * com o status HTTP já carregado pela exceção JAX-RS lançada nos Services
 * (BadRequestException, ForbiddenException, NotFoundException, ...).
 * Exceções não mapeadas (bugs, falha de banco, etc.) viram 500 com mensagem
 * genérica — nunca expõem stack trace ou detalhes internos ao cliente; o
 * detalhe completo vai para o log do servidor.
 */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException wae && wae.getResponse().getStatus() < 500) {
            String message = exception.getMessage() != null ? exception.getMessage() : wae.getResponse().getStatusInfo().getReasonPhrase();
            return Response.status(wae.getResponse().getStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(message))
                .build();
        }

        LOG.error("Erro não tratado ao processar requisição", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ErrorResponse("Erro interno ao processar a requisição"))
            .build();
    }
}
