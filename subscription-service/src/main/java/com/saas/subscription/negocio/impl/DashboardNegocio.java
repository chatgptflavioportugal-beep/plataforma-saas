package com.saas.subscription.negocio.impl;

import com.saas.subscription.dto.response.DashboardModuleResponse;

import java.util.List;
import java.util.UUID;

public interface DashboardNegocio {

    List<DashboardModuleResponse> listModulesWithAccessStatus(UUID tenantId, UUID userId, String role);
}
