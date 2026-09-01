package com.saas.profile.negocio.impl;

import com.saas.profile.dto.tenant.IndividualTenantResponse;
import com.saas.profile.dto.tenant.MyTenantDto;
import com.saas.profile.dto.tenant.TenantProfileResponse;
import com.saas.profile.entity.Tenant;

import java.util.List;
import java.util.UUID;

/**
 * Perfil/criação de tenant (empresa ou individual).
 */
public interface TenantNegocio {

    Tenant createTenant(String name, String slug, UUID ownerId, String type);

    TenantProfileResponse getTenantProfile(UUID tenantId);

    IndividualTenantResponse ensureIndividualTenant(UUID userId);

    List<MyTenantDto> listMyTenants(UUID userId);
}
