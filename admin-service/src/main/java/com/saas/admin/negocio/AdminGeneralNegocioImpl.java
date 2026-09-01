package com.saas.admin.negocio;

import com.saas.admin.dao.AdminGeneralDAO;
import com.saas.admin.dto.CustomerDetailDTO;
import com.saas.admin.dto.CustomerSummaryDTO;
import com.saas.admin.dto.DashboardStatsDTO;
import com.saas.admin.dto.SubscriptionPageDTO;
import com.saas.admin.dto.SubscriptionsSummaryDTO;
import com.saas.admin.dto.SystemAdminDTO;
import com.saas.admin.negocio.impl.AdminAuditNegocio;
import com.saas.admin.negocio.impl.AdminGeneralNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AdminGeneralNegocioImpl implements AdminGeneralNegocio {

    @Inject
    AdminGeneralDAO dao;

    @Inject
    AdminAuditNegocio auditNegocio;

    @Override
    public DashboardStatsDTO stats() {
        return dao.fetchStats();
    }

    @Override
    public List<CustomerSummaryDTO> listCustomers(
            String search, Boolean hasIndividual, Boolean hasOwnedCompany, Boolean isMember,
            Boolean isActive, String profileType) {
        return dao.findCustomers(search, hasIndividual, hasOwnedCompany, isMember, isActive, profileType);
    }

    @Override
    public Optional<CustomerDetailDTO> getCustomerDetail(String id) {
        return dao.findCustomerDetail(id);
    }

    @Override
    @Transactional
    public boolean updateCustomerStatus(String id, boolean isActive, String statusLabel, String actorUserId) {
        int updated = dao.updateCustomerStatus(id, isActive);
        if (updated == 0) return false;

        auditNegocio.log(actorUserId, "customer." + statusLabel, "user_profiles", id, Map.of("isActive", isActive));
        return true;
    }

    @Override
    public List<SystemAdminDTO> listSystemAdmins() {
        return dao.findSystemAdmins();
    }

    @Override
    public SubscriptionsSummaryDTO getSubscriptionsSummary() {
        return dao.fetchSubscriptionsSummary();
    }

    @Override
    public SubscriptionPageDTO listSubscriptions(
            String search, String profileType, String profileId, String companyId, String userId,
            String moduleId, String planId, String billingCycle, String status,
            String startDateFrom, String startDateTo, String expiresIn, String renewalStatus,
            int page, int size) {

        int safeSize   = Math.min(Math.max(size, 1), 100);
        int safeOffset = Math.max(page, 0) * safeSize;

        AdminGeneralDAO.SubscriptionSearchFilters filters = new AdminGeneralDAO.SubscriptionSearchFilters(
            search, profileType, profileId, companyId, userId, moduleId, planId, billingCycle, status,
            startDateFrom, startDateTo, expiresIn, renewalStatus, safeSize, safeOffset);

        AdminGeneralDAO.SubscriptionListResult result = dao.findSubscriptions(filters);
        return new SubscriptionPageDTO(result.items(), result.total(), page, safeSize);
    }
}
