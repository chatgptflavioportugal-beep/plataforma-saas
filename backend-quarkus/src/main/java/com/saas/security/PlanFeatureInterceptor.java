package com.saas.security;

import com.saas.exception.PlanFeatureNotAvailableException;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

@RequiresPlanFeature("")
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class PlanFeatureInterceptor {

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
            throw new PlanFeatureNotAvailableException(featureKey, tenantCtx.getPlanCode(), "");
        }

        return ctx.proceed();
    }
}
