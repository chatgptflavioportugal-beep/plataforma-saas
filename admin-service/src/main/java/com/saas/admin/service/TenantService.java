package com.saas.admin.service;

import com.saas.admin.dao.TenantDAO;
import com.saas.admin.dto.TenantDetailDTO;
import com.saas.admin.dto.TenantSummaryDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Listagem/detalhe administrativo de tenants, usada por AdminResource.
 * Persistência isolada em {@link TenantDAO}.
 */
@ApplicationScoped
public class TenantService {

    @Inject
    TenantDAO dao;

    @Inject
    AdminAuditService auditService;

    public List<TenantSummaryDTO> listAdminTenants(String search, String status, Boolean hasExtraMembers) {
        return dao.findAdminTenants(search, status, hasExtraMembers);
    }

    public Optional<TenantDetailDTO> getAdminTenantDetail(String id) {
        return dao.findAdminTenantDetail(id);
    }

    @Transactional
    public boolean updateStatus(String id, String status, String actorUserId) {
        int updated = dao.updateTenantStatus(id, status);
        if (updated == 0) return false;

        auditService.log(actorUserId, "tenant." + status, "tenants", id, Map.of("status", status));
        return true;
    }
}
