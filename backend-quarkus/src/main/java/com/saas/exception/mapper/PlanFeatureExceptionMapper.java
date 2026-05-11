package com.saas.exception.mapper;

import com.saas.exception.PlanFeatureNotAvailableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class PlanFeatureExceptionMapper implements ExceptionMapper<PlanFeatureNotAvailableException> {

    @Override
    public Response toResponse(PlanFeatureNotAvailableException e) {
        return Response.status(403)
                .entity(Map.of(
                        "error", "PLAN_FEATURE_NOT_AVAILABLE",
                        "feature", e.getFeature(),
                        "message", "Seu plano não inclui acesso a esta funcionalidade",
                        "currentPlan", e.getCurrentPlan(),
                        "requiredPlan", e.getRequiredPlan(),
                        "upgradeUrl", "/app/billing/plans?feature=" + e.getFeature()
                ))
                .build();
    }
}
