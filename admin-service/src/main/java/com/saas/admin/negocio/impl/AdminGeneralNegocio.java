package com.saas.admin.negocio.impl;

import com.saas.admin.dto.CustomerDetailDTO;
import com.saas.admin.dto.CustomerSummaryDTO;
import com.saas.admin.dto.DashboardStatsDTO;
import com.saas.admin.dto.SubscriptionPageDTO;
import com.saas.admin.dto.SubscriptionsSummaryDTO;
import com.saas.admin.dto.SystemAdminDTO;

import java.util.List;
import java.util.Optional;

/**
 * Consultas administrativas gerais (dashboard, clientes, administradores
 * legados, assinaturas) usadas por AdminResource. Persistência isolada em
 * AdminGeneralDAO.
 */
public interface AdminGeneralNegocio {

    DashboardStatsDTO stats();

    List<CustomerSummaryDTO> listCustomers(
            String search, Boolean hasIndividual, Boolean hasOwnedCompany, Boolean isMember,
            Boolean isActive, String profileType);

    Optional<CustomerDetailDTO> getCustomerDetail(String id);

    boolean updateCustomerStatus(String id, boolean isActive, String statusLabel, String actorUserId);

    List<SystemAdminDTO> listSystemAdmins();

    SubscriptionsSummaryDTO getSubscriptionsSummary();

    SubscriptionPageDTO listSubscriptions(
            String search, String profileType, String profileId, String companyId, String userId,
            String moduleId, String planId, String billingCycle, String status,
            String startDateFrom, String startDateTo, String expiresIn, String renewalStatus,
            int page, int size);
}
