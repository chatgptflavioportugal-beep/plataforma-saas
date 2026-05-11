package com.saas.security;

import com.saas.exception.PlanFeatureNotAvailableException;
import com.saas.service.PlanService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

@RequiresPlanFeature("")
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class PlanFeatureInterceptor {

    @Inject
    PlanService planService;

    @Context
    SecurityContext securityContext;

    @AroundInvoke
    public Object check(InvocationContext ctx) throws Exception {
        RequiresPlanFeature annotation = ctx.getMethod().getAnnotation(RequiresPlanFeature.class);
        if (annotation == null) {
            annotation = ctx.getTarget().getClass().getAnnotation(RequiresPlanFeature.class);
        }

        if (annotation == null) {
            return ctx.proceed();
        }

        String featureKey = annotation.value();
        TenantContext tenantCtx = TenantContext.from(securityContext);

        if (!tenantCtx.hasFeature(featureKey)) {
            String requiredPlan = planService.getMinPlanCodeForFeature(featureKey);
            throw new PlanFeatureNotAvailableException(featureKey, tenantCtx.getPlanCode(), requiredPlan);
        }

        return ctx.proceed();
    }
}
