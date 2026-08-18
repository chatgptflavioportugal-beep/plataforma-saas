package com.saas.admin.dao;

import com.saas.admin.dto.CustomerDetailDTO;
import com.saas.admin.dto.CustomerSummaryDTO;
import com.saas.admin.dto.DashboardStatsDTO;
import com.saas.admin.dto.SubscriptionListItemDTO;
import com.saas.admin.dto.SubscriptionsSummaryDTO;
import com.saas.admin.dto.SystemAdminDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas administrativas gerais (dashboard, clientes, administradores
 * legados, assinaturas) usadas por AdminResource.
 */
@ApplicationScoped
public class AdminGeneralDAO {

    @Inject
    EntityManager em;

    // ─── Dashboard ─────────────────────────────────────────────────────────

    private long n(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    public DashboardStatsDTO fetchStats() {
        long totalBusinessTenants = n("SELECT COUNT(*) FROM tenants WHERE type = 'business'");
        long activeTenants        = n("SELECT COUNT(*) FROM tenants WHERE status = 'active' AND type = 'business'");
        long trialTenants         = n("SELECT COUNT(*) FROM tenants WHERE status = 'trial' AND type = 'business'");
        long suspendedTenants     = n("SELECT COUNT(*) FROM tenants WHERE status = 'suspended' AND type = 'business'");
        long totalUsers           = n("SELECT COUNT(*) FROM user_profiles WHERE system_role NOT IN ('SUPER_ADMIN','ADMIN','SUPPORT','FINANCE_ADMIN')");
        long totalPdfJobs         = n("SELECT COUNT(*) FROM pdf_jobs");
        long usersWithIndividual  = n(
            "SELECT COUNT(DISTINCT ut.user_id) FROM user_tenants ut " +
            "JOIN tenants t ON t.id = ut.tenant_id AND t.type = 'individual' WHERE ut.is_active = TRUE"
        );
        long totalMemberLinks = n(
            "SELECT COUNT(*) FROM user_tenants ut " +
            "JOIN tenants t ON t.id = ut.tenant_id AND t.type = 'business' " +
            "WHERE ut.role != 'owner' AND ut.is_active = TRUE"
        );
        long usersInMultipleCompanies = n(
            "SELECT COUNT(*) FROM (" +
            "  SELECT ut.user_id FROM user_tenants ut " +
            "  JOIN tenants t ON t.id = ut.tenant_id AND t.type = 'business' " +
            "  WHERE ut.is_active = TRUE GROUP BY ut.user_id HAVING COUNT(*) > 1" +
            ") sub"
        );
        long companiesWithoutExtraMembers = n(
            "SELECT COUNT(*) FROM tenants t WHERE t.type = 'business' " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM user_tenants ut WHERE ut.tenant_id = t.id AND ut.role != 'owner' AND ut.is_active = TRUE" +
            ")"
        );
        long companiesWithInvitedMembers = n(
            "SELECT COUNT(DISTINCT tenant_id) FROM invitations WHERE status = 'accepted'"
        );

        return new DashboardStatsDTO(
            totalBusinessTenants, activeTenants, trialTenants, suspendedTenants,
            totalUsers, totalPdfJobs, usersWithIndividual, totalUsers - usersWithIndividual,
            totalMemberLinks, usersInMultipleCompanies, companiesWithoutExtraMembers, companiesWithInvitedMembers);
    }

    // ─── Clientes ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<CustomerSummaryDTO> findCustomers(
            String search, Boolean hasIndividual, Boolean hasOwnedCompany, Boolean isMember,
            Boolean isActive, String profileType) {

        StringBuilder sql = new StringBuilder(
            "WITH cdata AS (" +
            "  SELECT" +
            "    up.id::text               AS id," +
            "    au.email," +
            "    up.full_name," +
            "    up.is_active," +
            "    up.created_at::text       AS created_at," +
            "    au.last_sign_in_at::text  AS last_sign_in_at," +
            "    EXISTS(" +
            "      SELECT 1 FROM tenants ti" +
            "      JOIN user_tenants uti ON uti.tenant_id = ti.id AND uti.user_id = up.id AND uti.is_active = TRUE" +
            "      WHERE ti.type = 'individual'" +
            "    ) AS has_individual_profile," +
            "    (SELECT COUNT(*) FROM tenants tb1" +
            "     JOIN user_tenants utb1 ON utb1.tenant_id = tb1.id AND utb1.user_id = up.id AND utb1.is_active = TRUE" +
            "     WHERE tb1.type = 'business' AND utb1.role = 'owner'" +
            "    )::int AS owned_companies_count," +
            "    (SELECT COUNT(*) FROM tenants tb2" +
            "     JOIN user_tenants utb2 ON utb2.tenant_id = tb2.id AND utb2.user_id = up.id AND utb2.is_active = TRUE" +
            "     WHERE tb2.type = 'business' AND utb2.role != 'owner'" +
            "    )::int AS member_companies_count" +
            "  FROM user_profiles up" +
            "  JOIN auth.users au ON au.id = up.id" +
            "  WHERE up.system_role NOT IN ('SUPER_ADMIN','ADMIN','SUPPORT','FINANCE_ADMIN')" +
            ") SELECT * FROM cdata WHERE 1=1"
        );

        Map<String, Object> params = new LinkedHashMap<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(full_name) LIKE LOWER(:search) OR LOWER(email) LIKE LOWER(:search))");
            params.put("search", "%" + search.trim() + "%");
        }
        if (hasIndividual != null) {
            sql.append(hasIndividual ? " AND has_individual_profile = TRUE" : " AND has_individual_profile = FALSE");
        }
        if (hasOwnedCompany != null) {
            sql.append(hasOwnedCompany ? " AND owned_companies_count > 0" : " AND owned_companies_count = 0");
        }
        if (isMember != null) {
            sql.append(isMember ? " AND member_companies_count > 0" : " AND member_companies_count = 0");
        }
        if (isActive != null) {
            sql.append(isActive ? " AND is_active = TRUE" : " AND is_active = FALSE");
        }
        if ("individual".equals(profileType)) {
            sql.append(" AND has_individual_profile = TRUE");
        } else if ("owned_company".equals(profileType)) {
            sql.append(" AND owned_companies_count > 0");
        } else if ("member_company".equals(profileType)) {
            sql.append(" AND member_companies_count > 0");
        }
        sql.append(" ORDER BY created_at DESC");

        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(row -> {
            int owned  = row[7] instanceof Number n1 ? n1.intValue() : 0;
            int member = row[8] instanceof Number n2 ? n2.intValue() : 0;
            boolean hasInd = Boolean.TRUE.equals(row[6]);
            return new CustomerSummaryDTO(
                (String) row[0], (String) row[1], (String) row[2], (Boolean) row[3], (String) row[4],
                (String) row[5], (Boolean) row[6], (Integer) row[7], (Integer) row[8],
                (hasInd ? 1 : 0) + owned + member);
        }).toList();
    }

    @SuppressWarnings("unchecked")
    public Optional<CustomerDetailDTO> findCustomerDetail(String id) {
        List<Object[]> userRows = em.createNativeQuery(
            "SELECT up.id::text, au.email, up.full_name, up.is_active, up.created_at::text, au.last_sign_in_at::text " +
            "FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id " +
            "WHERE up.id::text = :id " +
            "AND up.system_role NOT IN ('SUPER_ADMIN','ADMIN','SUPPORT','FINANCE_ADMIN')"
        ).setParameter("id", id).getResultList();

        if (userRows.isEmpty()) return Optional.empty();
        Object[] u = userRows.get(0);

        List<Object[]> indRows = em.createNativeQuery(
            "SELECT t.id::text, t.name, t.slug, t.status, t.created_at::text " +
            "FROM tenants t " +
            "JOIN user_tenants ut ON ut.tenant_id = t.id AND ut.user_id::text = :id AND ut.is_active = TRUE " +
            "WHERE t.type = 'individual'"
        ).setParameter("id", id).getResultList();

        CustomerDetailDTO.IndividualProfileDTO individualProfile = null;
        if (!indRows.isEmpty()) {
            Object[] ip = indRows.get(0);
            individualProfile = new CustomerDetailDTO.IndividualProfileDTO(
                (String) ip[0], (String) ip[1], (String) ip[2], (String) ip[3], (String) ip[4]);
        }

        List<Object[]> ownedRows = em.createNativeQuery(
            "SELECT t.id::text, t.name, t.slug, t.status, t.created_at::text, " +
            "  p.name AS plan_name, p.code AS plan_code, " +
            "  (SELECT COUNT(*) FROM user_tenants ut2 WHERE ut2.tenant_id = t.id AND ut2.is_active = TRUE)::int " +
            "FROM tenants t " +
            "JOIN user_tenants ut ON ut.tenant_id = t.id AND ut.user_id::text = :id AND ut.role = 'owner' AND ut.is_active = TRUE " +
            "LEFT JOIN plans p ON p.id = t.plan_id " +
            "WHERE t.type = 'business' " +
            "ORDER BY t.created_at DESC"
        ).setParameter("id", id).getResultList();

        List<CustomerDetailDTO.OwnedCompanyDTO> ownedCompanies = ownedRows.stream().map(row ->
            new CustomerDetailDTO.OwnedCompanyDTO(
                (String) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4],
                (String) row[5], (String) row[6], (Integer) row[7])
        ).toList();

        List<Object[]> memberRows = em.createNativeQuery(
            "SELECT t.id::text, t.name, t.slug, ut.role, ut.is_active, ut.created_at::text, " +
            "  (SELECT up2.full_name FROM invitations inv " +
            "   JOIN user_profiles up2 ON up2.id = inv.invited_by " +
            "   WHERE inv.tenant_id = t.id " +
            "   AND inv.email = (SELECT email FROM auth.users WHERE id::text = :id) " +
            "   AND inv.status = 'accepted' LIMIT 1) " +
            "FROM tenants t " +
            "JOIN user_tenants ut ON ut.tenant_id = t.id AND ut.user_id::text = :id AND ut.role != 'owner' " +
            "WHERE t.type = 'business' " +
            "ORDER BY ut.created_at DESC"
        ).setParameter("id", id).getResultList();

        List<CustomerDetailDTO.MemberCompanyDTO> memberCompanies = memberRows.stream().map(row ->
            new CustomerDetailDTO.MemberCompanyDTO(
                (String) row[0], (String) row[1], (String) row[2], (String) row[3], (Boolean) row[4],
                (String) row[5], (String) row[6])
        ).toList();

        return Optional.of(new CustomerDetailDTO(
            (String) u[0], (String) u[1], (String) u[2], (Boolean) u[3], (String) u[4], (String) u[5],
            individualProfile, ownedCompanies, memberCompanies));
    }

    public int updateCustomerStatus(String id, boolean isActive) {
        return em.createNativeQuery(
            "UPDATE user_profiles SET is_active = :isActive, updated_at = NOW() WHERE id::text = :id"
        ).setParameter("isActive", isActive).setParameter("id", id).executeUpdate();
    }

    // ─── Administradores do sistema (papéis legados) ──────────────────────

    @SuppressWarnings("unchecked")
    public List<SystemAdminDTO> findSystemAdmins() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT up.id::text, au.email, up.full_name, up.system_role, up.is_active, " +
                "up.created_at::text " +
                "FROM user_profiles up " +
                "JOIN auth.users au ON au.id = up.id " +
                "WHERE up.system_role IN ('SUPER_ADMIN', 'ADMIN', 'SUPPORT', 'FINANCE_ADMIN') " +
                "ORDER BY up.system_role, up.created_at DESC"
        ).getResultList();

        return rows.stream().map(row -> new SystemAdminDTO(
            (String) row[0], (String) row[1], (String) row[2], (String) row[3], (Boolean) row[4], (String) row[5]
        )).toList();
    }

    // ─── Assinaturas ───────────────────────────────────────────────────────

    public SubscriptionsSummaryDTO fetchSubscriptionsSummary() {
        Object[] row = (Object[]) em.createNativeQuery(
            "SELECT COUNT(*)::bigint, " +
            "COUNT(*) FILTER (WHERE status = 'ACTIVE')::bigint, " +
            "COUNT(*) FILTER (WHERE billing_cycle = 'MONTHLY')::bigint, " +
            "COUNT(*) FILTER (WHERE billing_cycle = 'ANNUAL')::bigint, " +
            "COUNT(*) FILTER (WHERE status = 'CANCELED')::bigint, " +
            "COUNT(*) FILTER (WHERE status = 'EXPIRED')::bigint, " +
            "COUNT(*) FILTER (WHERE status = 'PENDING_PAYMENT')::bigint, " +
            "COUNT(*) FILTER (WHERE status = 'TRIAL')::bigint, " +
            "COUNT(*) FILTER (WHERE status = 'TRIAL_CANCELLED')::bigint " +
            "FROM profile_module_subscriptions"
        ).getSingleResult();

        return new SubscriptionsSummaryDTO(
            ((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue(),
            ((Number) row[3]).longValue(), ((Number) row[4]).longValue(), ((Number) row[5]).longValue(),
            ((Number) row[6]).longValue(), ((Number) row[7]).longValue(), ((Number) row[8]).longValue());
    }

    public record SubscriptionSearchFilters(
        String search, String profileType, String profileId, String companyId, String userId,
        String moduleId, String planId, String billingCycle, String status,
        String startDateFrom, String startDateTo, String expiresIn, String renewalStatus,
        int size, int offset) {
    }

    public record SubscriptionListResult(List<SubscriptionListItemDTO> items, long total) {
    }

    @SuppressWarnings("unchecked")
    public SubscriptionListResult findSubscriptions(SubscriptionSearchFilters f) {
        StringBuilder sql = new StringBuilder(
            "SELECT pms.id::text, " +
            "t.id::text, t.name, t.type, " +
            "CASE WHEN t.type = 'business' THEN t.id::text ELSE NULL END, " +
            "CASE WHEN t.type = 'business' THEN t.name ELSE NULL END, " +
            "CASE WHEN t.type = 'business' THEN t.slug ELSE NULL END, " +
            "up.id::text, up.full_name, au.email, " +
            "pm.id::text, pm.name, pm.icon_path, " +
            "p.id::text, p.name, pvm.id::text, p.version, " +
            "pms.billing_cycle, " +
            "CASE WHEN pms.billing_cycle = 'MONTHLY' THEN pvm.monthly_price ELSE pvm.annual_monthly_price * 12 END, " +
            "CASE WHEN pms.billing_cycle = 'ANNUAL' THEN pvm.annual_monthly_price * 12 ELSE NULL END, " +
            "pms.status, pms.started_at::text, pms.expires_at::text, pms.canceled_at::text, " +
            "(pms.status = 'ACTIVE'), COUNT(*) OVER() " +
            "FROM profile_module_subscriptions pms " +
            "JOIN tenants t ON t.id = pms.tenant_id " +
            "JOIN platform_modules pm ON pm.id = pms.module_id " +
            "JOIN plan_version_modules pvm ON pvm.id = pms.plan_version_id " +
            "JOIN plans p ON p.id = pvm.plan_id " +
            "LEFT JOIN user_tenants ut ON ut.tenant_id = t.id AND ut.role = 'owner' AND ut.is_active = TRUE " +
            "LEFT JOIN user_profiles up ON up.id = ut.user_id " +
            "LEFT JOIN auth.users au ON au.id = ut.user_id " +
            "WHERE 1=1"
        );

        Map<String, Object> params = new LinkedHashMap<>();

        if (f.search() != null && !f.search().isBlank()) {
            sql.append(" AND (LOWER(t.name) LIKE LOWER(:search)" +
                       " OR LOWER(pm.name) LIKE LOWER(:search)" +
                       " OR LOWER(p.name) LIKE LOWER(:search)" +
                       " OR LOWER(COALESCE(up.full_name,'')) LIKE LOWER(:search)" +
                       " OR LOWER(COALESCE(au.email,'')) LIKE LOWER(:search))");
            params.put("search", "%" + f.search().trim() + "%");
        }
        if (f.profileType() != null && !f.profileType().isBlank()) {
            sql.append("INDIVIDUAL".equalsIgnoreCase(f.profileType().trim())
                ? " AND t.type = 'individual'"
                : " AND t.type = 'business'");
        }
        if (f.profileId() != null && !f.profileId().isBlank()) {
            sql.append(" AND t.id::text = :profileId");
            params.put("profileId", f.profileId().trim());
        }
        if (f.companyId() != null && !f.companyId().isBlank()) {
            sql.append(" AND t.id::text = :companyId AND t.type = 'business'");
            params.put("companyId", f.companyId().trim());
        }
        if (f.userId() != null && !f.userId().isBlank()) {
            sql.append(" AND (up.id::text = :userId OR LOWER(COALESCE(au.email,'')) LIKE LOWER(:userSearch))");
            params.put("userId", f.userId().trim());
            params.put("userSearch", "%" + f.userId().trim() + "%");
        }
        if (f.moduleId() != null && !f.moduleId().isBlank()) {
            sql.append(" AND pm.id::text = :moduleId");
            params.put("moduleId", f.moduleId().trim());
        }
        if (f.planId() != null && !f.planId().isBlank()) {
            sql.append(" AND p.id::text = :planId");
            params.put("planId", f.planId().trim());
        }
        if (f.billingCycle() != null && !f.billingCycle().isBlank()) {
            sql.append(" AND pms.billing_cycle = :billingCycle");
            params.put("billingCycle", f.billingCycle().toUpperCase().trim());
        }
        if (f.status() != null && !f.status().isBlank()) {
            sql.append(" AND pms.status = :status");
            params.put("status", f.status().toUpperCase().trim());
        }
        if (f.startDateFrom() != null && !f.startDateFrom().isBlank()) {
            sql.append(" AND pms.started_at >= CAST(:startDateFrom AS TIMESTAMPTZ)");
            params.put("startDateFrom", f.startDateFrom().trim());
        }
        if (f.startDateTo() != null && !f.startDateTo().isBlank()) {
            sql.append(" AND pms.started_at <= CAST(:startDateTo AS TIMESTAMPTZ)");
            params.put("startDateTo", f.startDateTo().trim());
        }
        if (f.expiresIn() != null && !f.expiresIn().isBlank()) {
            switch (f.expiresIn().trim().toLowerCase()) {
                case "7"       -> sql.append(" AND pms.expires_at BETWEEN NOW() AND NOW() + INTERVAL '7 days'");
                case "15"      -> sql.append(" AND pms.expires_at BETWEEN NOW() AND NOW() + INTERVAL '15 days'");
                case "30"      -> sql.append(" AND pms.expires_at BETWEEN NOW() AND NOW() + INTERVAL '30 days'");
                case "overdue" -> sql.append(" AND pms.expires_at < NOW()");
                case "none"    -> sql.append(" AND pms.expires_at IS NULL");
                default        -> {}
            }
        }
        if (f.renewalStatus() != null && !f.renewalStatus().isBlank()) {
            if ("active".equalsIgnoreCase(f.renewalStatus().trim())) {
                sql.append(" AND pms.status = 'ACTIVE'");
            } else if ("canceled".equalsIgnoreCase(f.renewalStatus().trim())) {
                sql.append(" AND pms.status IN ('CANCELED','EXPIRED')");
            }
        }

        sql.append(" ORDER BY pms.started_at DESC LIMIT :size OFFSET :offset");
        params.put("size",   f.size());
        params.put("offset", f.offset());

        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<Object[]> rows = query.getResultList();

        long totalCount = rows.isEmpty() ? 0L : ((Number) rows.get(0)[25]).longValue();

        List<SubscriptionListItemDTO> items = rows.stream().map(row -> new SubscriptionListItemDTO(
            (String) row[0], (String) row[1], (String) row[2],
            "business".equals(row[3]) ? "COMPANY" : "INDIVIDUAL",
            (String) row[4], (String) row[5], (String) row[6],
            (String) row[7], (String) row[8], (String) row[9],
            (String) row[10], (String) row[11], (String) row[12],
            (String) row[13], (String) row[14], (String) row[15],
            row[16] != null ? ((Number) row[16]).intValue() : null,
            (String) row[17], (BigDecimal) row[18], (BigDecimal) row[19],
            (String) row[20], (String) row[21], (String) row[22], (String) row[23],
            (Boolean) row[24]
        )).toList();

        return new SubscriptionListResult(items, totalCount);
    }
}
