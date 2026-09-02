package com.saas.admin.dao;

import com.saas.admin.dto.CustomerDetailDTO;
import com.saas.admin.dto.CustomerSummaryDTO;
import com.saas.admin.dto.DashboardStatsDTO;
import com.saas.admin.dto.SubscriptionListItemDTO;
import com.saas.admin.dto.SubscriptionsSummaryDTO;
import com.saas.admin.dto.SystemAdminDTO;
import com.saas.admin.to.AdminUserTO;
import com.saas.admin.to.CustomerSummaryTO;
import com.saas.admin.to.CustomerUserTO;
import com.saas.admin.to.IndividualProfileTO;
import com.saas.admin.to.MemberCompanyTO;
import com.saas.admin.to.OwnedCompanyTO;
import com.saas.admin.to.SubscriptionListItemTO;
import com.saas.admin.to.SubscriptionsSummaryTO;
import com.saas.platformdatabase.query.DatabaseQuery;
import com.saas.platformdatabase.query.NativeQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

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

    @Inject
    DatabaseQuery databaseQuery;

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

        NativeQuery<CustomerSummaryTO> query = databaseQuery.nativeQuery(em, sql.toString(), CustomerSummaryTO.class);
        params.forEach(query::setParameter);
        List<CustomerSummaryTO> rows = query.getResultList();

        return rows.stream().map(row -> {
            int owned = row.ownedCompaniesCount() != null ? row.ownedCompaniesCount() : 0;
            int member = row.memberCompaniesCount() != null ? row.memberCompaniesCount() : 0;
            boolean hasInd = Boolean.TRUE.equals(row.hasIndividualProfile());
            return new CustomerSummaryDTO(
                row.id(), row.email(), row.fullName(), row.isActive(), row.createdAt(),
                row.lastSignInAt(), row.hasIndividualProfile(), row.ownedCompaniesCount(), row.memberCompaniesCount(),
                (hasInd ? 1 : 0) + owned + member);
        }).toList();
    }

    public Optional<CustomerDetailDTO> findCustomerDetail(String id) {
        CustomerUserTO u = databaseQuery
                .nativeQuery(em, """
                        SELECT up.id::text, au.email, up.full_name, up.is_active, up.created_at::text, au.last_sign_in_at::text
                        FROM user_profiles up
                        JOIN auth.users au ON au.id = up.id
                        WHERE up.id::text = :id
                        AND up.system_role NOT IN ('SUPER_ADMIN','ADMIN','SUPPORT','FINANCE_ADMIN')
                        """, CustomerUserTO.class)
                .setParameter("id", id)
                .getOptionalResult()
                .orElse(null);

        if (u == null) return Optional.empty();

        Optional<IndividualProfileTO> indRow = databaseQuery
                .nativeQuery(em, """
                        SELECT t.id::text, t.name, t.slug, t.status, t.created_at::text
                        FROM tenants t
                        JOIN user_tenants ut ON ut.tenant_id = t.id AND ut.user_id::text = :id AND ut.is_active = TRUE
                        WHERE t.type = 'individual'
                        """, IndividualProfileTO.class)
                .setParameter("id", id)
                .getOptionalResult();

        CustomerDetailDTO.IndividualProfileDTO individualProfile = indRow
                .map(ip -> new CustomerDetailDTO.IndividualProfileDTO(ip.id(), ip.name(), ip.slug(), ip.status(), ip.createdAt()))
                .orElse(null);

        List<OwnedCompanyTO> ownedRows = databaseQuery
                .nativeQuery(em, """
                        SELECT t.id::text, t.name, t.slug, t.status, t.created_at::text,
                          p.name AS plan_name, p.code AS plan_code,
                          (SELECT COUNT(*) FROM user_tenants ut2 WHERE ut2.tenant_id = t.id AND ut2.is_active = TRUE)::int AS member_count
                        FROM tenants t
                        JOIN user_tenants ut ON ut.tenant_id = t.id AND ut.user_id::text = :id AND ut.role = 'owner' AND ut.is_active = TRUE
                        LEFT JOIN plans p ON p.id = t.plan_id
                        WHERE t.type = 'business'
                        ORDER BY t.created_at DESC
                        """, OwnedCompanyTO.class)
                .setParameter("id", id)
                .getResultList();

        List<CustomerDetailDTO.OwnedCompanyDTO> ownedCompanies = ownedRows.stream().map(row ->
            new CustomerDetailDTO.OwnedCompanyDTO(
                row.id(), row.name(), row.slug(), row.status(), row.createdAt(),
                row.planName(), row.planCode(), row.memberCount())
        ).toList();

        List<MemberCompanyTO> memberRows = databaseQuery
                .nativeQuery(em, """
                        SELECT t.id::text, t.name, t.slug, ut.role, ut.is_active, ut.created_at::text,
                          (SELECT up2.full_name FROM invitations inv
                           JOIN user_profiles up2 ON up2.id = inv.invited_by
                           WHERE inv.tenant_id = t.id
                           AND inv.email = (SELECT email FROM auth.users WHERE id::text = :id)
                           AND inv.status = 'accepted' LIMIT 1) AS invited_by_name
                        FROM tenants t
                        JOIN user_tenants ut ON ut.tenant_id = t.id AND ut.user_id::text = :id AND ut.role != 'owner'
                        WHERE t.type = 'business'
                        ORDER BY ut.created_at DESC
                        """, MemberCompanyTO.class)
                .setParameter("id", id)
                .getResultList();

        List<CustomerDetailDTO.MemberCompanyDTO> memberCompanies = memberRows.stream().map(row ->
            new CustomerDetailDTO.MemberCompanyDTO(
                row.id(), row.name(), row.slug(), row.role(), row.linkActive(),
                row.joinedAt(), row.invitedByName())
        ).toList();

        return Optional.of(new CustomerDetailDTO(
            u.id(), u.email(), u.fullName(), u.isActive(), u.createdAt(), u.lastSignInAt(),
            individualProfile, ownedCompanies, memberCompanies));
    }

    public int updateCustomerStatus(String id, boolean isActive) {
        return em.createNativeQuery(
            "UPDATE user_profiles SET is_active = :isActive, updated_at = NOW() WHERE id::text = :id"
        ).setParameter("isActive", isActive).setParameter("id", id).executeUpdate();
    }

    // ─── Administradores do sistema (papéis legados) ──────────────────────

    public List<SystemAdminDTO> findSystemAdmins() {
        List<AdminUserTO> rows = databaseQuery
                .nativeQuery(em, """
                        SELECT up.id::text, au.email, up.full_name, up.system_role, up.is_active,
                        up.created_at::text
                        FROM user_profiles up
                        JOIN auth.users au ON au.id = up.id
                        WHERE up.system_role IN ('SUPER_ADMIN', 'ADMIN', 'SUPPORT', 'FINANCE_ADMIN')
                        ORDER BY up.system_role, up.created_at DESC
                        """, AdminUserTO.class)
                .getResultList();

        return rows.stream().map(row -> new SystemAdminDTO(
            row.id(), row.email(), row.fullName(), row.systemRole(), row.isActive(), row.createdAt()
        )).toList();
    }

    // ─── Assinaturas ───────────────────────────────────────────────────────

    public SubscriptionsSummaryDTO fetchSubscriptionsSummary() {
        SubscriptionsSummaryTO row = databaseQuery
                .nativeQuery(em, """
                        SELECT COUNT(*)::bigint AS total_count,
                        COUNT(*) FILTER (WHERE status = 'ACTIVE')::bigint AS active_count,
                        COUNT(*) FILTER (WHERE billing_cycle = 'MONTHLY')::bigint AS monthly_count,
                        COUNT(*) FILTER (WHERE billing_cycle = 'ANNUAL')::bigint AS annual_count,
                        COUNT(*) FILTER (WHERE status = 'CANCELED')::bigint AS canceled_count,
                        COUNT(*) FILTER (WHERE status = 'EXPIRED')::bigint AS expired_count,
                        COUNT(*) FILTER (WHERE status = 'PENDING_PAYMENT')::bigint AS pending_payment_count,
                        COUNT(*) FILTER (WHERE status = 'TRIAL')::bigint AS trial_count,
                        COUNT(*) FILTER (WHERE status = 'TRIAL_CANCELLED')::bigint AS trial_cancelled_count
                        FROM profile_module_subscriptions
                        """, SubscriptionsSummaryTO.class)
                .getOptionalResult()
                .orElseThrow();

        return new SubscriptionsSummaryDTO(
            row.total(), row.active(), row.monthly(),
            row.annual(), row.canceled(), row.expired(),
            row.pendingPayment(), row.trial(), row.trialCancelled());
    }

    public record SubscriptionSearchFilters(
        String search, String profileType, String profileId, String companyId, String userId,
        String moduleId, String planId, String billingCycle, String status,
        String startDateFrom, String startDateTo, String expiresIn, String renewalStatus,
        int size, int offset) {
    }

    public record SubscriptionListResult(List<SubscriptionListItemDTO> items, long total) {
    }

    public SubscriptionListResult findSubscriptions(SubscriptionSearchFilters f) {
        StringBuilder sql = new StringBuilder(
            "SELECT pms.id::text AS id, " +
            "t.id::text AS profile_id, t.name AS profile_name, t.type AS profile_type, " +
            "CASE WHEN t.type = 'business' THEN t.id::text ELSE NULL END AS company_id, " +
            "CASE WHEN t.type = 'business' THEN t.name ELSE NULL END AS company_name, " +
            "CASE WHEN t.type = 'business' THEN t.slug ELSE NULL END AS company_slug, " +
            "up.id::text AS owner_user_id, up.full_name AS owner_name, au.email AS owner_email, " +
            "pm.id::text AS module_id, pm.name AS module_name, pm.icon_path AS module_icon_path, " +
            "p.id::text AS plan_id, p.name AS plan_name, pvm.id::text AS plan_version_id, p.version AS plan_version_number, " +
            "pms.billing_cycle AS billing_cycle, " +
            "CASE WHEN pms.billing_cycle = 'MONTHLY' THEN pvm.monthly_price ELSE pvm.annual_monthly_price * 12 END AS price, " +
            "CASE WHEN pms.billing_cycle = 'ANNUAL' THEN pvm.annual_monthly_price * 12 ELSE NULL END AS annual_total_price, " +
            "pms.status AS status, pms.started_at::text AS started_at, pms.expires_at::text AS expires_at, pms.canceled_at::text AS canceled_at, " +
            "(pms.status = 'ACTIVE') AS renewal_active, COUNT(*) OVER() AS total_count " +
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

        NativeQuery<SubscriptionListItemTO> query = databaseQuery.nativeQuery(em, sql.toString(), SubscriptionListItemTO.class);
        params.forEach(query::setParameter);
        List<SubscriptionListItemTO> rows = query.getResultList();

        long totalCount = rows.isEmpty() ? 0L : rows.get(0).totalCount();

        List<SubscriptionListItemDTO> items = rows.stream().map(row -> new SubscriptionListItemDTO(
            row.id(), row.profileId(), row.profileName(),
            "business".equals(row.profileType()) ? "COMPANY" : "INDIVIDUAL",
            row.companyId(), row.companyName(), row.companySlug(),
            row.ownerUserId(), row.ownerName(), row.ownerEmail(),
            row.moduleId(), row.moduleName(), row.moduleIconPath(),
            row.planId(), row.planName(), row.planVersionId(), row.planVersionNumber(),
            row.billingCycle(), row.price(), row.annualTotalPrice(),
            row.status(), row.startedAt(), row.expiresAt(), row.canceledAt(),
            row.renewalActive()
        )).toList();

        return new SubscriptionListResult(items, totalCount);
    }
}
