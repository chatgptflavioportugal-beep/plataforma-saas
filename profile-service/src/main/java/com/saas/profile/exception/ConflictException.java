package com.saas.profile.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/** Regra de negócio impede a operação porque o recurso está em uso por outro (HTTP 409). */
public class ConflictException extends WebApplicationException {

    public ConflictException(String message) {
        super(message, Response.Status.CONFLICT);
    }
}
