package com.saas.admin.controller;

import com.saas.admin.security.AdminAuthService;
import com.saas.admin.service.AdminAuditService;
import com.saas.admin.service.TenantService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * Tenants, clientes, administradores do sistema, assinaturas (visão admin) e stats
 * do dashboard admin. Migrado 1:1 do backend-quarkus (com.saas.resource.AdminResource).
 */
@Path("/api/v1/admin")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject TenantService tenantService;
    @Inject EntityManager em;
    @Inject AdminAuthService adminAuth;
    @Inject AdminAuditService auditService;

    // ----------------------------------------------------------------
    // Dashboard stats
    // ----------------------------------------------------------------

    @GET
    @Path("/stats")
    public Response stats() {
        adminAuth.requireAdminPermission("admin.dashboard.view");
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
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total_tenants", totalBusinessTenants);
        result.put("active_tenants", activeTenants);
        result.put("trial_tenants", trialTenants);
        result.put("suspended_tenants", suspendedTenants);
        result.put("total_users", totalUsers);
        result.put("total_pdf_jobs", totalPdfJobs);
        result.put("users_with_individual_profile", usersWithIndividual);
        result.put("users_without_individual_profile", totalUsers - usersWithIndividual);
        result.put("total_member_links", totalMemberLinks);
        result.put("users_in_multiple_companies", usersInMultipleCompanies);
        result.put("companies_without_extra_members", companiesWithoutExtraMembers);
        result.put("companies_with_invited_members", companiesWithInvitedMembers);
        return Response.ok(result).build();
    }

    // ----------------------------------------------------------------
    // Tenants
    // ----------------------------------------------------------------

    @GET
    @Path("/tenants")
    public Response listTenants(
            @QueryParam("search") String search,
            @QueryParam("status") String status,
            @QueryParam("has_extra_members") Boolean hasExtraMembers) {
        adminAuth.requireAdminPermission("admin.companies.view");
        return Response.ok(tenantService.listAdminTenants(search, status, hasExtraMembers)).build();
    }

    @GET
    @Path("/tenants/{id}")
    public Response getTenantDetail(@PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.companies.detail");
        var detail = tenantService.getAdminTenantDetail(id);
        if (detail == null)
            return Response.status(404).entity(Map.of("error", "Empresa não encontrada")).build();
        return Response.ok(detail).build();
    }

    // ----------------------------------------------------------------
    // Clientes — listagem com estrutura completa de perfis
    // ----------------------------------------------------------------

    @GET
    @Path("/customers")
    @SuppressWarnings("unchecked")
    public Response listCustomers(
            @QueryParam("search") String search,
            @QueryParam("has_individual") Boolean hasIndividual,
            @QueryParam("has_owned_company") Boolean hasOwnedCompany,
            @QueryParam("is_member") Boolean isMember,
            @QueryParam("is_active") Boolean isActive,
            @QueryParam("profile_type") String profileType) {
        adminAuth.requireAdminPermission("admin.clients.view");

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

        Map<String, Object> params = new java.util.LinkedHashMap<>();
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

        var query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);

        List<Object[]> rows = (List<Object[]>) query.getResultList();
        var customers = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("email", row[1]);
            m.put("full_name", row[2]);
            m.put("is_active", row[3]);
            m.put("created_at", row[4]);
            m.put("last_sign_in_at", row[5]);
            m.put("has_individual_profile", row[6]);
            m.put("owned_companies_count", row[7]);
            m.put("member_companies_count", row[8]);
            int owned  = row[7] instanceof Number n1 ? n1.intValue() : 0;
            int member = row[8] instanceof Number n2 ? n2.intValue() : 0;
            boolean hasInd = Boolean.TRUE.equals(row[6]);
            m.put("total_profiles", (hasInd ? 1 : 0) + owned + member);
            return m;
        }).toList();
        return Response.ok(customers).build();
    }

    // ----------------------------------------------------------------
    // Clientes — detalhe por ID
    // ----------------------------------------------------------------

    @GET
    @Path("/customers/{id}")
    @SuppressWarnings("unchecked")
    public Response getCustomerDetail(@PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.clients.detail");

        List<Object[]> userRows = (List<Object[]>) em.createNativeQuery(
            "SELECT up.id::text, au.email, up.full_name, up.is_active, up.created_at::text, au.last_sign_in_at::text " +
            "FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id " +
            "WHERE up.id::text = :id " +
            "AND up.system_role NOT IN ('SUPER_ADMIN','ADMIN','SUPPORT','FINANCE_ADMIN')"
        ).setParameter("id", id).getResultList();

        if (userRows.isEmpty())
            return Response.status(404).entity(Map.of("error", "Usuário não encontrado")).build();

        Object[] u = userRows.get(0);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", u[0]);
        result.put("email", u[1]);
        result.put("full_name", u[2]);
        result.put("is_active", u[3]);
        result.put("created_at", u[4]);
        result.put("last_sign_in_at", u[5]);

        // Perfil individual
        List<Object[]> indRows = (List<Object[]>) em.createNativeQuery(
            "SELECT t.id::text, t.name, t.slug, t.status, t.created_at::text " +
            "FROM tenants t " +
            "JOIN user_tenants ut ON ut.tenant_id = t.id AND ut.user_id::text = :id AND ut.is_active = TRUE " +
            "WHERE t.type = 'individual'"
        ).setParameter("id", id).getResultList();

        if (indRows.isEmpty()) {
            result.put("individual_profile", null);
        } else {
            Object[] ip = indRows.get(0);
            Map<String, Object> ipMap = new java.util.LinkedHashMap<>();
            ipMap.put("id", ip[0]);
            ipMap.put("name", ip[1]);
            ipMap.put("slug", ip[2]);
            ipMap.put("status", ip[3]);
            ipMap.put("created_at", ip[4]);
            result.put("individual_profile", ipMap);
        }

        // Empresas criadas (owner)
        List<Object[]> ownedRows = (List<Object[]>) em.createNativeQuery(
            "SELECT t.id::text, t.name, t.slug, t.status, t.created_at::text, " +
            "  p.name AS plan_name, p.code AS plan_code, " +
            "  (SELECT COUNT(*) FROM user_tenants ut2 WHERE ut2.tenant_id = t.id AND ut2.is_active = TRUE)::int " +
            "FROM tenants t " +
            "JOIN user_tenants ut ON ut.tenant_id = t.id AND ut.user_id::text = :id AND ut.role = 'owner' AND ut.is_active = TRUE " +
            "LEFT JOIN plans p ON p.id = t.plan_id " +
            "WHERE t.type = 'business' " +
            "ORDER BY t.created_at DESC"
        ).setParameter("id", id).getResultList();

        result.put("owned_companies", ownedRows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            m.put("slug", row[2]);
            m.put("status", row[3]);
            m.put("created_at", row[4]);
            m.put("plan_name", row[5]);
            m.put("plan_code", row[6]);
            m.put("member_count", row[7]);
            return m;
        }).toList());

        // Empresas onde é membro
        List<Object[]> memberRows = (List<Object[]>) em.createNativeQuery(
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

        result.put("member_companies", memberRows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            m.put("slug", row[2]);
            m.put("role", row[3]);
            m.put("link_active", row[4]);
            m.put("joined_at", row[5]);
            m.put("invited_by_name", row[6]);
            return m;
        }).toList());

        return Response.ok(result).build();
    }

    // ----------------------------------------------------------------
    // Compatibilidade — redireciona chamadas legadas de company-users
    // ----------------------------------------------------------------

    @GET
    @Path("/company-users")
    public Response listCompanyUsersLegacy() {
        return listCustomers(null, null, null, null, null, null);
    }

    // ----------------------------------------------------------------
    // Administradores do Sistema (somente roles administrativos)
    // ----------------------------------------------------------------

    @GET
    @Path("/system-admins")
    @SuppressWarnings("unchecked")
    public Response listSystemAdmins() {
        adminAuth.requireAdminPermission("admin.users.view");
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
                "SELECT up.id::text, au.email, up.full_name, up.system_role, up.is_active, " +
                "up.created_at::text " +
                "FROM user_profiles up " +
                "JOIN auth.users au ON au.id = up.id " +
                "WHERE up.system_role IN ('SUPER_ADMIN', 'ADMIN', 'SUPPORT', 'FINANCE_ADMIN') " +
                "ORDER BY up.system_role, up.created_at DESC"
        ).getResultList();
        var admins = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("email", row[1]);
            m.put("full_name", row[2]);
            m.put("system_role", row[3]);
            m.put("is_active", row[4]);
            m.put("created_at", row[5]);
            return m;
        }).toList();
        return Response.ok(admins).build();
    }

    // ----------------------------------------------------------------
    // Assinaturas por perfil e módulo
    // ----------------------------------------------------------------

    @GET
    @Path("/subscriptions/summary")
    public Response getSubscriptionsSummary() {
        adminAuth.requireAdminPermission("admin.subscriptions.view");
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
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("total",          ((Number) row[0]).longValue());
        m.put("active",         ((Number) row[1]).longValue());
        m.put("monthly",        ((Number) row[2]).longValue());
        m.put("annual",         ((Number) row[3]).longValue());
        m.put("canceled",       ((Number) row[4]).longValue());
        m.put("expired",        ((Number) row[5]).longValue());
        m.put("pendingPayment", ((Number) row[6]).longValue());
        m.put("trial",          ((Number) row[7]).longValue());
        m.put("trialCancelled", ((Number) row[8]).longValue());
        return Response.ok(m).build();
    }

    @GET
    @Path("/subscriptions")
    @SuppressWarnings("unchecked")
    public Response listSubscriptions(
            @QueryParam("search")        String search,
            @QueryParam("profileType")   String profileType,
            @QueryParam("profileId")     String profileId,
            @QueryParam("companyId")     String companyId,
            @QueryParam("userId")        String userId,
            @QueryParam("moduleId")      String moduleId,
            @QueryParam("planId")        String planId,
            @QueryParam("billingCycle")  String billingCycle,
            @QueryParam("status")        String status,
            @QueryParam("startDateFrom") String startDateFrom,
            @QueryParam("startDateTo")   String startDateTo,
            @QueryParam("expiresIn")     String expiresIn,
            @QueryParam("renewalStatus") String renewalStatus,
            @QueryParam("page")          @DefaultValue("0") int page,
            @QueryParam("size")          @DefaultValue("20") int size
    ) {
        adminAuth.requireAdminPermission("admin.subscriptions.view");

        int safeSize   = Math.min(Math.max(size, 1), 100);
        int safeOffset = Math.max(page, 0) * safeSize;

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

        Map<String, Object> params = new java.util.LinkedHashMap<>();

        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(t.name) LIKE LOWER(:search)" +
                       " OR LOWER(pm.name) LIKE LOWER(:search)" +
                       " OR LOWER(p.name) LIKE LOWER(:search)" +
                       " OR LOWER(COALESCE(up.full_name,'')) LIKE LOWER(:search)" +
                       " OR LOWER(COALESCE(au.email,'')) LIKE LOWER(:search))");
            params.put("search", "%" + search.trim() + "%");
        }
        if (profileType != null && !profileType.isBlank()) {
            sql.append("INDIVIDUAL".equalsIgnoreCase(profileType.trim())
                ? " AND t.type = 'individual'"
                : " AND t.type = 'business'");
        }
        if (profileId != null && !profileId.isBlank()) {
            sql.append(" AND t.id::text = :profileId");
            params.put("profileId", profileId.trim());
        }
        if (companyId != null && !companyId.isBlank()) {
            sql.append(" AND t.id::text = :companyId AND t.type = 'business'");
            params.put("companyId", companyId.trim());
        }
        if (userId != null && !userId.isBlank()) {
            sql.append(" AND (up.id::text = :userId OR LOWER(COALESCE(au.email,'')) LIKE LOWER(:userSearch))");
            params.put("userId", userId.trim());
            params.put("userSearch", "%" + userId.trim() + "%");
        }
        if (moduleId != null && !moduleId.isBlank()) {
            sql.append(" AND pm.id::text = :moduleId");
            params.put("moduleId", moduleId.trim());
        }
        if (planId != null && !planId.isBlank()) {
            sql.append(" AND p.id::text = :planId");
            params.put("planId", planId.trim());
        }
        if (billingCycle != null && !billingCycle.isBlank()) {
            sql.append(" AND pms.billing_cycle = :billingCycle");
            params.put("billingCycle", billingCycle.toUpperCase().trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND pms.status = :status");
            params.put("status", status.toUpperCase().trim());
        }
        if (startDateFrom != null && !startDateFrom.isBlank()) {
            sql.append(" AND pms.started_at >= CAST(:startDateFrom AS TIMESTAMPTZ)");
            params.put("startDateFrom", startDateFrom.trim());
        }
        if (startDateTo != null && !startDateTo.isBlank()) {
            sql.append(" AND pms.started_at <= CAST(:startDateTo AS TIMESTAMPTZ)");
            params.put("startDateTo", startDateTo.trim());
        }
        if (expiresIn != null && !expiresIn.isBlank()) {
            switch (expiresIn.trim().toLowerCase()) {
                case "7"       -> sql.append(" AND pms.expires_at BETWEEN NOW() AND NOW() + INTERVAL '7 days'");
                case "15"      -> sql.append(" AND pms.expires_at BETWEEN NOW() AND NOW() + INTERVAL '15 days'");
                case "30"      -> sql.append(" AND pms.expires_at BETWEEN NOW() AND NOW() + INTERVAL '30 days'");
                case "overdue" -> sql.append(" AND pms.expires_at < NOW()");
                case "none"    -> sql.append(" AND pms.expires_at IS NULL");
                default        -> {}
            }
        }
        if (renewalStatus != null && !renewalStatus.isBlank()) {
            if ("active".equalsIgnoreCase(renewalStatus.trim())) {
                sql.append(" AND pms.status = 'ACTIVE'");
            } else if ("canceled".equalsIgnoreCase(renewalStatus.trim())) {
                sql.append(" AND pms.status IN ('CANCELED','EXPIRED')");
            }
        }

        sql.append(" ORDER BY pms.started_at DESC LIMIT :size OFFSET :offset");
        params.put("size",   safeSize);
        params.put("offset", safeOffset);

        var query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<Object[]> rows = (List<Object[]>) query.getResultList();

        long totalCount = rows.isEmpty() ? 0L : ((Number) rows.get(0)[25]).longValue();

        var items = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id",                row[0]);
            m.put("profileId",         row[1]);
            m.put("profileName",       row[2]);
            m.put("profileType",       "business".equals(row[3]) ? "COMPANY" : "INDIVIDUAL");
            m.put("companyId",         row[4]);
            m.put("companyName",       row[5]);
            m.put("companySlug",       row[6]);
            m.put("ownerUserId",       row[7]);
            m.put("ownerName",         row[8]);
            m.put("ownerEmail",        row[9]);
            m.put("moduleId",          row[10]);
            m.put("moduleName",        row[11]);
            m.put("moduleIconPath",    row[12]);
            m.put("planId",            row[13]);
            m.put("planName",          row[14]);
            m.put("planVersionId",     row[15]);
            m.put("planVersionNumber", row[16]);
            m.put("billingCycle",      row[17]);
            m.put("price",             row[18]);
            m.put("annualTotalPrice",  row[19]);
            m.put("status",            row[20]);
            m.put("startedAt",         row[21]);
            m.put("expiresAt",         row[22]);
            m.put("canceledAt",        row[23]);
            m.put("renewalActive",     row[24]);
            return m;
        }).toList();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("items", items);
        result.put("total", totalCount);
        result.put("page",  page);
        result.put("size",  safeSize);
        return Response.ok(result).build();
    }

    // Cancelamento/reativação de assinatura são operações do domínio do
    // subscription-service (profile_module_subscriptions) — o frontend-admin
    // chama subscription-service diretamente em vez de passar por aqui.

    // ----------------------------------------------------------------
    // Gestão Global — Bloqueio/Desbloqueio/Suspensão/Ativação
    // ----------------------------------------------------------------

    private static final List<String> VALID_TENANT_STATUSES = List.of("trial", "active", "suspended", "cancelled");

    public record UpdateStatusRequest(String status) {}

    @PATCH
    @Path("/tenants/{id}/status")
    @Transactional
    public Response updateTenantStatus(@PathParam("id") String id, UpdateStatusRequest req) {
        String status = req != null ? req.status() : null;
        if (status == null || !VALID_TENANT_STATUSES.contains(status))
            return Response.status(400).entity(Map.of("error", "status inválido: deve ser um de " + VALID_TENANT_STATUSES)).build();

        adminAuth.requireAdminPermission("active".equals(status) ? "admin.companies.activate" : "admin.companies.deactivate");

        int updated = em.createNativeQuery(
            "UPDATE tenants SET status = :status, updated_at = NOW() WHERE id::text = :id"
        ).setParameter("status", status).setParameter("id", id).executeUpdate();

        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Empresa não encontrada")).build();

        auditService.log(adminAuth.currentUserId(), "tenant." + status, "tenants", id, Map.of("status", status));
        return Response.ok(Map.of("id", id, "status", status)).build();
    }

    @PATCH
    @Path("/customers/{id}/status")
    @Transactional
    public Response updateCustomerStatus(@PathParam("id") String id, UpdateStatusRequest req) {
        String status = req != null ? req.status() : null;
        if (!List.of("active", "inactive").contains(status))
            return Response.status(400).entity(Map.of("error", "status inválido: deve ser 'active' ou 'inactive'")).build();

        adminAuth.requireAdminPermission("active".equals(status) ? "admin.clients.activate" : "admin.clients.deactivate");

        boolean isActive = "active".equals(status);
        int updated = em.createNativeQuery(
            "UPDATE user_profiles SET is_active = :isActive, updated_at = NOW() WHERE id::text = :id"
        ).setParameter("isActive", isActive).setParameter("id", id).executeUpdate();

        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Cliente não encontrado")).build();

        auditService.log(adminAuth.currentUserId(), "customer." + status, "user_profiles", id, Map.of("isActive", isActive));
        return Response.ok(Map.of("id", id, "status", status)).build();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private long n(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

}
