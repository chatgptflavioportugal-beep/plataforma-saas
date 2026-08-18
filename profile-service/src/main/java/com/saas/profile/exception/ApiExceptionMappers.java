package com.saas.profile.exception;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Padroniza o corpo de erro `{"error": "mensagem"}` para as exceções de negócio lançadas
 * pelos Services, no lugar de cada Resource montar a Response manualmente em um catch.
 */
final class ApiExceptionMappers {
    private ApiExceptionMappers() {}

    static Response errorResponse(Response.Status status, String message) {
        return Response.status(status)
                .entity(Map.of("error", message == null ? "" : message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}

@Provider
class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {
    @Override
    public Response toResponse(BadRequestException exception) {
        return ApiExceptionMappers.errorResponse(Response.Status.BAD_REQUEST, exception.getMessage());
    }
}

@Provider
class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {
    @Override
    public Response toResponse(ForbiddenException exception) {
        return ApiExceptionMappers.errorResponse(Response.Status.FORBIDDEN, exception.getMessage());
    }
}

@Provider
class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {
    @Override
    public Response toResponse(NotFoundException exception) {
        return ApiExceptionMappers.errorResponse(Response.Status.NOT_FOUND, exception.getMessage());
    }
}

@Provider
class ConflictExceptionMapper implements ExceptionMapper<ConflictException> {
    @Override
    public Response toResponse(ConflictException exception) {
        return ApiExceptionMappers.errorResponse(Response.Status.CONFLICT, exception.getMessage());
    }
}
