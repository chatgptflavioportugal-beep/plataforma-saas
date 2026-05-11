package com.saas.exception.mapper;

import com.saas.exception.TrialExpiredException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class TrialExpiredExceptionMapper implements ExceptionMapper<TrialExpiredException> {

    @Override
    public Response toResponse(TrialExpiredException e) {
        return Response.status(402)
                .entity(Map.of(
                        "error", "TRIAL_EXPIRED",
                        "message", e.getMessage(),
                        "upgradeUrl", "/app/billing/plans"
                ))
                .build();
    }
}
