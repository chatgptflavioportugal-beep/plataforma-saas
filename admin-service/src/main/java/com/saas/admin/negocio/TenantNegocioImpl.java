package com.saas.admin.negocio;

import com.saas.admin.dao.TenantDAO;
import com.saas.admin.dto.TenantDetailDTO;
import com.saas.admin.dto.TenantSummaryDTO;
import com.saas.admin.negocio.impl.AdminAuditNegocio;
import com.saas.admin.negocio.impl.TenantNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class TenantNegocioImpl implements TenantNegocio {

    @Inject
    TenantDAO dao;

    @Inject
    AdminAuditNegocio auditNegocio;

    @Override
    public List<TenantSummaryDTO> listAdminTenants(String search, String status, Boolean hasExtraMembers) {
        return dao.findAdminTenants(search, status, hasExtraMembers);
    }

    @Override
    public Optional<TenantDetailDTO> getAdminTenantDetail(String id) {
        return dao.findAdminTenantDetail(id);
    }

    @Override
    @Transactional
    public boolean updateStatus(String id, String status, String actorUserId) {
        int updated = dao.updateTenantStatus(id, status);
        if (updated == 0) return false;

        auditNegocio.log(actorUserId, "tenant." + status, "tenants", id, Map.of("status", status));
        return true;
    }
}
