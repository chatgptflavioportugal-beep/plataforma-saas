package com.saas.admin.negocio.impl;

import com.saas.admin.dto.TenantDetailDTO;
import com.saas.admin.dto.TenantSummaryDTO;

import java.util.List;
import java.util.Optional;

/**
 * Listagem/detalhe administrativo de tenants, usada por AdminResource.
 * Persistência isolada em TenantDAO.
 */
public interface TenantNegocio {

    List<TenantSummaryDTO> listAdminTenants(String search, String status, Boolean hasExtraMembers);

    Optional<TenantDetailDTO> getAdminTenantDetail(String id);

    boolean updateStatus(String id, String status, String actorUserId);
}
