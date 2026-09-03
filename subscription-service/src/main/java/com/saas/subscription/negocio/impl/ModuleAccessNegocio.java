package com.saas.subscription.negocio.impl;

import com.saas.subscription.dto.response.ModuleAccessResolutionResponse;
import com.saas.platformtenant.TenantContext;

public interface ModuleAccessNegocio {

    ModuleAccessResolutionResponse resolve(String moduleSlug, TenantContext ctx);
}
