package com.saas.subscription.negocio.impl;

import com.saas.subscription.dto.response.ModuleAccessResolutionResponse;
import com.saas.subscription.security.TenantContext;

public interface ModuleAccessNegocio {

    ModuleAccessResolutionResponse resolve(String moduleSlug, TenantContext ctx);
}
