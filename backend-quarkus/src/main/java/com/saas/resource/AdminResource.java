package com.saas.resource;

import com.saas.service.PlanService;
import com.saas.service.TenantService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Path("/api/v1/admin")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject TenantService tenantService;
    @Inject PlanService   planService;
    @Inject EntityManager em;
    @Inject JsonWebToken  jwt;

    // ----------------------------------------------------------------
    // Autorização
    // ----------------------------------------------------------------

    String currentUserId() {
        String userId = jwt.getSubject();
        if (userId == null) throw new ForbiddenException("Não autenticado");
        return userId;
    }

    /**
     * Exige que o usuário seja SUPER_ADMIN ou ADMIN_USER ativo com a permissão especificada.
     * Passar null em permissionKey verifica apenas que é um admin válido (para listagens gerais).
     */
    void requireAdminPermission(String permissionKey) {
        String userId = currentUserId();
        try {
            Object[] row = (Object[]) em.createNativeQuery(
                "SELECT system_role, is_active, admin_access_level_id::text " +
                "FROM user_profiles WHERE id::text = :id"
            ).setParameter("id", userId).getSingleResult();

            String role = (String) row[0];
            boolean isActive = Boolean.TRUE.equals(row[1]);

            if ("SUPER_ADMIN".equals(role)) return;

            if (!"ADMIN_USER".equals(role))
                throw new ForbiddenException("Acesso restrito à área administrativa");

            if (!isActive)
                throw new ForbiddenException("Usuário administrativo inativo");

            if (permissionKey == null) return;

            String accessLevelId = (String) row[2];
            if (accessLevelId == null)
                throw new ForbiddenException("Você não possui permissão para executar esta ação");

            long has = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM admin_access_level_permissions " +
                "WHERE access_level_id::text = :lvl AND permission_key = :key"
            ).setParameter("lvl", accessLevelId).setParameter("key", permissionKey)
             .getSingleResult()).longValue();

            if (has == 0)
                throw new ForbiddenException("Você não possui permissão para executar esta ação");

        } catch (jakarta.persistence.NoResultException e) {
            throw new ForbiddenException("Perfil de usuário não encontrado");
        }
    }

    /** Mantido para compatibilidade interna; usa requireAdminPermission com null. */
    private void ensureSuperAdmin() {
        requireAdminPermission(null);
    }

    private static final java.util.regex.Pattern SLUG_PATTERN =
        java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9_-]*$|^[a-z0-9]$");

    private boolean isValidSlug(String slug) {
        return slug != null && !slug.isBlank() && SLUG_PATTERN.matcher(slug).matches();
    }

    // ----------------------------------------------------------------
    // Dashboard stats
    // ----------------------------------------------------------------

    @GET
    @Path("/stats")
    public Response stats(@Context SecurityContext ctx) {
        requireAdminPermission("admin.dashboard.view");
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
        requireAdminPermission("admin.companies.view");
        return Response.ok(tenantService.listAdminTenants(search, status, hasExtraMembers)).build();
    }

    @GET
    @Path("/tenants/{id}")
    public Response getTenantDetail(@PathParam("id") String id) {
        requireAdminPermission("admin.companies.detail");
        var detail = tenantService.getAdminTenantDetail(id);
        if (detail == null)
            return Response.status(404).entity(Map.of("error", "Empresa não encontrada")).build();
        return Response.ok(detail).build();
    }

    // ----------------------------------------------------------------
    // Planos — listagem e histórico
    // ----------------------------------------------------------------

    @GET
    @Path("/plans")
    public Response listPlans() {
        requireAdminPermission("admin.plans.view");
        return Response.ok(planService.listAllPlansAdmin()).build();
    }

    @GET
    @Path("/plans/{code}/versions")
    public Response getPlanVersions(@PathParam("code") String code) {
        requireAdminPermission("admin.plans.version_history");
        return Response.ok(planService.getPlanVersionHistory(code)).build();
    }

    // ----------------------------------------------------------------
    // Planos — criar (v1)
    // ----------------------------------------------------------------

    @POST
    @Path("/plans")
    public Response createPlan(Map<String, Object> body) {
        requireAdminPermission("admin.plans.create");
        var req = mapToRequest(body);
        if (req.code() == null || req.code().isBlank())
            return Response.status(400).entity(Map.of("error", "code é obrigatório")).build();
        if (req.name() == null || req.name().isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();

        return Response.status(201).entity(planService.createPlan(req)).build();
    }

    // ----------------------------------------------------------------
    // Planos — gerar nova versão (substitui edição direta)
    // ----------------------------------------------------------------

    @POST
    @Path("/plans/{id}/new-version")
    public Response createNewVersion(@PathParam("id") String id, Map<String, Object> body) {
        requireAdminPermission("admin.plans.create_version");
        try {
            var req = mapToRequest(body);
            return Response.status(201).entity(planService.createNewVersion(id, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Planos — edição unificada (dados + módulos + limitações → nova versão)
    // ----------------------------------------------------------------

    @POST
    @Path("/plans/{id}/edit")
    public Response editPlanWithNewVersion(@PathParam("id") String id, Map<String, Object> body) {
        requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToRequest(body);
            var modules = mapToPlanModuleWithLimitsRequests(body);
            return Response.status(201).entity(planService.createNewVersionWithModules(id, req, modules)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Planos — ativar / desativar
    // ----------------------------------------------------------------

    @PATCH
    @Path("/plans/{id}/status")
    @Consumes(MediaType.WILDCARD)
    public Response togglePlanStatus(@PathParam("id") String id) {
        requireAdminPermission("admin.plans.activate");
        try {
            return Response.ok(planService.togglePlanStatus(id)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Planos — definir "Mais Popular"
    // ----------------------------------------------------------------

    @PATCH
    @Path("/plans/{id}/popular")
    @Consumes(MediaType.WILDCARD)
    public Response setMostPopular(@PathParam("id") String id) {
        requireAdminPermission("admin.plans.edit");
        try {
            return Response.ok(planService.setMostPopular(id)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
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
        requireAdminPermission("admin.clients.view");

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
        requireAdminPermission("admin.clients.detail");

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
        requireAdminPermission("admin.users.view");
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
        requireAdminPermission("admin.subscriptions.view");
        Object[] row = (Object[]) em.createNativeQuery(
            "SELECT COUNT(*)::bigint, " +
            "COUNT(*) FILTER (WHERE status = 'ACTIVE')::bigint, " +
            "COUNT(*) FILTER (WHERE billing_cycle = 'MONTHLY')::bigint, " +
            "COUNT(*) FILTER (WHERE billing_cycle = 'ANNUAL')::bigint, " +
            "COUNT(*) FILTER (WHERE status = 'CANCELED')::bigint, " +
            "COUNT(*) FILTER (WHERE status = 'EXPIRED')::bigint, " +
            "COUNT(*) FILTER (WHERE status = 'PENDING_PAYMENT')::bigint " +
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
        requireAdminPermission("admin.subscriptions.view");

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

    @POST
    @Path("/subscriptions/{id}/cancel")
    @Transactional
    public Response adminCancelSubscription(@PathParam("id") String id) {
        requireAdminPermission("admin.subscriptions.cancel");
        int updated = em.createNativeQuery(
            "UPDATE profile_module_subscriptions " +
            "SET status = 'CANCELED', canceled_at = NOW(), updated_at = NOW() " +
            "WHERE id::text = :id AND status = 'ACTIVE'"
        ).setParameter("id", id).executeUpdate();
        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Assinatura não encontrada ou já cancelada")).build();
        return Response.ok(Map.of("success", true, "id", id, "status", "CANCELED")).build();
    }

    @POST
    @Path("/subscriptions/{id}/reactivate")
    @Transactional
    public Response adminReactivateSubscription(@PathParam("id") String id) {
        requireAdminPermission("admin.subscriptions.reactivate");
        int updated = em.createNativeQuery(
            "UPDATE profile_module_subscriptions " +
            "SET status = 'ACTIVE', canceled_at = NULL, updated_at = NOW() " +
            "WHERE id::text = :id AND status = 'CANCELED' AND (expires_at IS NULL OR expires_at > NOW())"
        ).setParameter("id", id).executeUpdate();
        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Assinatura não encontrada, não está cancelada ou já expirou")).build();
        return Response.ok(Map.of("success", true, "id", id, "status", "ACTIVE")).build();
    }

    // ----------------------------------------------------------------
    // Módulos da plataforma
    // ----------------------------------------------------------------

    @GET
    @Path("/modules")
    @SuppressWarnings("unchecked")
    public Response listModules(@QueryParam("search") String search, @QueryParam("is_active") Boolean isActive) {
        requireAdminPermission("admin.modules.view");
        StringBuilder sql = new StringBuilder(
            "SELECT m.id::text, m.name, m.slug, m.description, m.module_url, m.icon_path, " +
            "  m.is_active, m.sort_order, m.created_at::text, m.updated_at::text, " +
            "  (SELECT COUNT(*) FROM platform_module_services s WHERE s.module_id = m.id)::int AS service_count " +
            "FROM platform_modules m WHERE 1=1"
        );
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(m.name) LIKE LOWER(:search) OR LOWER(m.slug) LIKE LOWER(:search))");
            params.put("search", "%" + search.trim() + "%");
        }
        if (isActive != null) {
            sql.append(isActive ? " AND m.is_active = TRUE" : " AND m.is_active = FALSE");
        }
        sql.append(" ORDER BY m.sort_order, m.name");
        var query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<Object[]> rows = (List<Object[]>) query.getResultList();
        var modules = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            m.put("slug", row[2]);
            m.put("description", row[3]);
            m.put("module_url", row[4]);
            m.put("icon_path", row[5]);
            m.put("is_active", row[6]);
            m.put("sort_order", row[7]);
            m.put("created_at", row[8]);
            m.put("updated_at", row[9]);
            m.put("service_count", row[10]);
            return m;
        }).toList();
        return Response.ok(modules).build();
    }

    @POST
    @Path("/modules")
    @Transactional
    public Response createModule(Map<String, Object> body) {
        requireAdminPermission("admin.modules.create");
        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        String moduleUrl = (String) body.get("module_url");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();
        if (slug == null || slug.isBlank())
            return Response.status(400).entity(Map.of("error", "slug é obrigatório")).build();
        if (!isValidSlug(slug))
            return Response.status(400).entity(Map.of("error", "Slug inválido. Use apenas letras minúsculas, números e hífen")).build();
        if (moduleUrl == null || moduleUrl.isBlank())
            return Response.status(400).entity(Map.of("error", "module_url é obrigatório")).build();

        long existing = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_modules WHERE slug = :slug"
        ).setParameter("slug", slug).getSingleResult()).longValue();
        if (existing > 0)
            return Response.status(400).entity(Map.of("error", "Já existe um módulo com este slug")).build();

        String description = (String) body.get("description");
        String iconPath = (String) body.get("icon_path");
        boolean isActive = body.get("is_active") == null || Boolean.TRUE.equals(body.get("is_active"));
        int sortOrder = body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "INSERT INTO platform_modules (name, slug, description, module_url, icon_path, is_active, sort_order) " +
            "VALUES (:name, :slug, :description, :moduleUrl, :iconPath, :isActive, :sortOrder) " +
            "RETURNING id::text, name, slug, description, module_url, icon_path, is_active, sort_order, created_at::text, updated_at::text"
        )
        .setParameter("name", name)
        .setParameter("slug", slug)
        .setParameter("description", description)
        .setParameter("moduleUrl", moduleUrl)
        .setParameter("iconPath", iconPath)
        .setParameter("isActive", isActive)
        .setParameter("sortOrder", sortOrder)
        .getResultList();

        Object[] r = rows.get(0);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", r[0]); result.put("name", r[1]); result.put("slug", r[2]);
        result.put("description", r[3]); result.put("module_url", r[4]); result.put("icon_path", r[5]);
        result.put("is_active", r[6]); result.put("sort_order", r[7]);
        result.put("created_at", r[8]); result.put("updated_at", r[9]);
        result.put("service_count", 0);
        return Response.status(201).entity(result).build();
    }

    @PATCH
    @Path("/modules/{id}")
    @Transactional
    public Response updateModule(@PathParam("id") String id, Map<String, Object> body) {
        requireAdminPermission("admin.modules.edit");
        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        String moduleUrl = (String) body.get("module_url");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();
        if (slug == null || slug.isBlank())
            return Response.status(400).entity(Map.of("error", "slug é obrigatório")).build();
        if (!isValidSlug(slug))
            return Response.status(400).entity(Map.of("error", "Slug inválido. Use apenas letras minúsculas, números e hífen")).build();
        if (moduleUrl == null || moduleUrl.isBlank())
            return Response.status(400).entity(Map.of("error", "module_url é obrigatório")).build();

        long existing = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_modules WHERE slug = :slug AND id::text != :id"
        ).setParameter("slug", slug).setParameter("id", id).getSingleResult()).longValue();
        if (existing > 0)
            return Response.status(400).entity(Map.of("error", "Já existe outro módulo com este slug")).build();

        int updated = em.createNativeQuery(
            "UPDATE platform_modules SET name = :name, slug = :slug, description = :description, " +
            "module_url = :moduleUrl, icon_path = :iconPath, is_active = :isActive, sort_order = :sortOrder " +
            "WHERE id::text = :id"
        )
        .setParameter("name", name)
        .setParameter("slug", slug)
        .setParameter("description", body.get("description"))
        .setParameter("moduleUrl", moduleUrl)
        .setParameter("iconPath", body.get("icon_path"))
        .setParameter("isActive", body.get("is_active") == null || Boolean.TRUE.equals(body.get("is_active")))
        .setParameter("sortOrder", body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99)
        .setParameter("id", id)
        .executeUpdate();

        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Módulo não encontrado")).build();

        return Response.ok(Map.of("ok", true)).build();
    }

    @PATCH
    @Path("/modules/{id}/status")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public Response toggleModuleStatus(@PathParam("id") String id) {
        requireAdminPermission("admin.modules.activate");
        int updated = em.createNativeQuery(
            "UPDATE platform_modules SET is_active = NOT is_active WHERE id::text = :id"
        ).setParameter("id", id).executeUpdate();
        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Módulo não encontrado")).build();
        return Response.ok(Map.of("ok", true)).build();
    }

    // ----------------------------------------------------------------
    // Serviços/Itens dos módulos
    // ----------------------------------------------------------------

    @GET
    @Path("/modules/{moduleId}/services")
    @SuppressWarnings("unchecked")
    public Response listModuleServices(@PathParam("moduleId") String moduleId) {
        requireAdminPermission("admin.services.view");
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "SELECT s.id::text, s.module_id::text, s.name, s.slug, s.description, " +
            "  s.icon_path, s.is_active, s.sort_order, s.created_at::text, s.updated_at::text, " +
            "  s.service_group_id::text, g.name AS group_name " +
            "FROM platform_module_services s " +
            "LEFT JOIN platform_module_service_groups g ON g.id = s.service_group_id " +
            "WHERE s.module_id::text = :moduleId " +
            "ORDER BY COALESCE(g.sort_order, 9999), s.sort_order, s.name"
        ).setParameter("moduleId", moduleId).getResultList();
        var services = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]); m.put("module_id", row[1]); m.put("name", row[2]);
            m.put("slug", row[3]); m.put("description", row[4]);
            m.put("icon_path", row[5]); m.put("is_active", row[6]); m.put("sort_order", row[7]);
            m.put("created_at", row[8]); m.put("updated_at", row[9]);
            m.put("service_group_id", row[10]); m.put("service_group_name", row[11]);
            return m;
        }).toList();
        return Response.ok(services).build();
    }

    @POST
    @Path("/modules/{moduleId}/services")
    @Transactional
    @SuppressWarnings("unchecked")
    public Response createModuleService(@PathParam("moduleId") String moduleId, Map<String, Object> body) {
        requireAdminPermission("admin.services.create");
        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();
        if (slug == null || slug.isBlank())
            return Response.status(400).entity(Map.of("error", "slug é obrigatório")).build();
        if (!isValidSlug(slug))
            return Response.status(400).entity(Map.of("error", "Slug inválido. Use apenas letras minúsculas, números e hífen")).build();

        long moduleExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_modules WHERE id::text = :id"
        ).setParameter("id", moduleId).getSingleResult()).longValue();
        if (moduleExists == 0)
            return Response.status(404).entity(Map.of("error", "Módulo não encontrado")).build();

        long slugExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_services WHERE module_id::text = :moduleId AND slug = :slug"
        ).setParameter("moduleId", moduleId).setParameter("slug", slug).getSingleResult()).longValue();
        if (slugExists > 0)
            return Response.status(400).entity(Map.of("error", "Já existe um serviço com este slug neste módulo")).build();

        String serviceGroupIdStr = (String) body.get("service_group_id");
        java.util.UUID serviceGroupId = null;
        if (serviceGroupIdStr != null && !serviceGroupIdStr.isBlank()) {
            try { serviceGroupId = java.util.UUID.fromString(serviceGroupIdStr); }
            catch (IllegalArgumentException e) {
                return Response.status(400).entity(Map.of("error", "service_group_id inválido")).build();
            }
            long groupExists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM platform_module_service_groups WHERE id = CAST(:gid AS uuid) AND module_id::text = :moduleId"
            ).setParameter("gid", serviceGroupId.toString()).setParameter("moduleId", moduleId).getSingleResult()).longValue();
            if (groupExists == 0)
                return Response.status(400).entity(Map.of("error", "Grupo não encontrado neste módulo")).build();
        }

        boolean isActive = body.get("is_active") == null || Boolean.TRUE.equals(body.get("is_active"));
        int sortOrder = body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99;

        final java.util.UUID finalGroupId = serviceGroupId;
        List<Object[]> rows;
        if (finalGroupId != null) {
            rows = (List<Object[]>) em.createNativeQuery(
                "INSERT INTO platform_module_services (module_id, service_group_id, name, slug, description, icon_path, is_active, sort_order) " +
                "VALUES (CAST(:moduleId AS uuid), CAST(:serviceGroupId AS uuid), :name, :slug, :description, :iconPath, :isActive, :sortOrder) " +
                "RETURNING id::text, module_id::text, name, slug, description, icon_path, is_active, sort_order, created_at::text, updated_at::text, service_group_id::text"
            )
            .setParameter("moduleId", moduleId).setParameter("serviceGroupId", finalGroupId.toString())
            .setParameter("name", name).setParameter("slug", slug)
            .setParameter("description", body.get("description")).setParameter("iconPath", body.get("icon_path"))
            .setParameter("isActive", isActive).setParameter("sortOrder", sortOrder)
            .getResultList();
        } else {
            rows = (List<Object[]>) em.createNativeQuery(
                "INSERT INTO platform_module_services (module_id, service_group_id, name, slug, description, icon_path, is_active, sort_order) " +
                "VALUES (CAST(:moduleId AS uuid), NULL, :name, :slug, :description, :iconPath, :isActive, :sortOrder) " +
                "RETURNING id::text, module_id::text, name, slug, description, icon_path, is_active, sort_order, created_at::text, updated_at::text, service_group_id::text"
            )
            .setParameter("moduleId", moduleId).setParameter("name", name).setParameter("slug", slug)
            .setParameter("description", body.get("description")).setParameter("iconPath", body.get("icon_path"))
            .setParameter("isActive", isActive).setParameter("sortOrder", sortOrder)
            .getResultList();
        }

        Object[] r = rows.get(0);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", r[0]); result.put("module_id", r[1]); result.put("name", r[2]);
        result.put("slug", r[3]); result.put("description", r[4]);
        result.put("icon_path", r[5]); result.put("is_active", r[6]); result.put("sort_order", r[7]);
        result.put("created_at", r[8]); result.put("updated_at", r[9]);
        result.put("service_group_id", r[10]); result.put("service_group_name", null);
        return Response.status(201).entity(result).build();
    }

    @PATCH
    @Path("/modules/{moduleId}/services/{id}")
    @Transactional
    public Response updateModuleService(
            @PathParam("moduleId") String moduleId,
            @PathParam("id") String id,
            Map<String, Object> body) {
        requireAdminPermission("admin.services.edit");
        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();
        if (slug == null || slug.isBlank())
            return Response.status(400).entity(Map.of("error", "slug é obrigatório")).build();
        if (!isValidSlug(slug))
            return Response.status(400).entity(Map.of("error", "Slug inválido. Use apenas letras minúsculas, números e hífen")).build();

        long slugExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_services WHERE module_id::text = :moduleId AND slug = :slug AND id::text != :id"
        ).setParameter("moduleId", moduleId).setParameter("slug", slug).setParameter("id", id).getSingleResult()).longValue();
        if (slugExists > 0)
            return Response.status(400).entity(Map.of("error", "Já existe outro serviço com este slug neste módulo")).build();

        String serviceGroupIdStr = (String) body.get("service_group_id");
        java.util.UUID serviceGroupId = null;
        if (serviceGroupIdStr != null && !serviceGroupIdStr.isBlank()) {
            try { serviceGroupId = java.util.UUID.fromString(serviceGroupIdStr); }
            catch (IllegalArgumentException e) {
                return Response.status(400).entity(Map.of("error", "service_group_id inválido")).build();
            }
            long groupExists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM platform_module_service_groups WHERE id = CAST(:gid AS uuid) AND module_id::text = :moduleId"
            ).setParameter("gid", serviceGroupId.toString()).setParameter("moduleId", moduleId).getSingleResult()).longValue();
            if (groupExists == 0)
                return Response.status(400).entity(Map.of("error", "Grupo não encontrado neste módulo")).build();
        }

        int updated;
        if (serviceGroupId != null) {
            updated = em.createNativeQuery(
                "UPDATE platform_module_services SET name = :name, slug = :slug, description = :description, " +
                "icon_path = :iconPath, is_active = :isActive, sort_order = :sortOrder, " +
                "service_group_id = CAST(:serviceGroupId AS uuid) " +
                "WHERE id::text = :id AND module_id::text = :moduleId"
            )
            .setParameter("name", name).setParameter("slug", slug)
            .setParameter("description", body.get("description")).setParameter("iconPath", body.get("icon_path"))
            .setParameter("isActive", body.get("is_active") == null || Boolean.TRUE.equals(body.get("is_active")))
            .setParameter("sortOrder", body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99)
            .setParameter("serviceGroupId", serviceGroupId.toString())
            .setParameter("id", id).setParameter("moduleId", moduleId)
            .executeUpdate();
        } else {
            updated = em.createNativeQuery(
                "UPDATE platform_module_services SET name = :name, slug = :slug, description = :description, " +
                "icon_path = :iconPath, is_active = :isActive, sort_order = :sortOrder, " +
                "service_group_id = NULL " +
                "WHERE id::text = :id AND module_id::text = :moduleId"
            )
            .setParameter("name", name).setParameter("slug", slug)
            .setParameter("description", body.get("description")).setParameter("iconPath", body.get("icon_path"))
            .setParameter("isActive", body.get("is_active") == null || Boolean.TRUE.equals(body.get("is_active")))
            .setParameter("sortOrder", body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99)
            .setParameter("id", id).setParameter("moduleId", moduleId)
            .executeUpdate();
        }

        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Serviço não encontrado")).build();

        return Response.ok(Map.of("ok", true)).build();
    }

    @PATCH
    @Path("/modules/{moduleId}/services/{id}/status")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public Response toggleModuleServiceStatus(
            @PathParam("moduleId") String moduleId,
            @PathParam("id") String id) {
        requireAdminPermission("admin.services.activate");
        int updated = em.createNativeQuery(
            "UPDATE platform_module_services SET is_active = NOT is_active WHERE id::text = :id AND module_id::text = :moduleId"
        ).setParameter("id", id).setParameter("moduleId", moduleId).executeUpdate();
        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Serviço não encontrado")).build();
        return Response.ok(Map.of("ok", true)).build();
    }

    // ----------------------------------------------------------------
    // Grupos de Serviços dos módulos
    // ----------------------------------------------------------------

    @GET
    @Path("/modules/{moduleId}/service-groups")
    @SuppressWarnings("unchecked")
    public Response listModuleServiceGroups(@PathParam("moduleId") String moduleId) {
        requireAdminPermission("admin.services.groups.manage");
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "SELECT g.id::text, g.module_id::text, g.name, g.description, g.icon_path, " +
            "  g.sort_order, g.status, g.created_at::text, g.updated_at::text, " +
            "  (SELECT COUNT(*) FROM platform_module_services s WHERE s.service_group_id = g.id)::int " +
            "FROM platform_module_service_groups g " +
            "WHERE g.module_id::text = :moduleId " +
            "ORDER BY g.sort_order, g.name"
        ).setParameter("moduleId", moduleId).getResultList();
        var groups = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]); m.put("module_id", row[1]); m.put("name", row[2]);
            m.put("description", row[3]); m.put("icon_path", row[4]);
            m.put("sort_order", row[5]); m.put("status", row[6]);
            m.put("created_at", row[7]); m.put("updated_at", row[8]);
            m.put("service_count", row[9]);
            return m;
        }).toList();
        return Response.ok(groups).build();
    }

    @POST
    @Path("/modules/{moduleId}/service-groups")
    @Transactional
    @SuppressWarnings("unchecked")
    public Response createModuleServiceGroup(@PathParam("moduleId") String moduleId, Map<String, Object> body) {
        requireAdminPermission("admin.services.groups.manage");
        String name = (String) body.get("name");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();

        long moduleExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_modules WHERE id::text = :id"
        ).setParameter("id", moduleId).getSingleResult()).longValue();
        if (moduleExists == 0)
            return Response.status(404).entity(Map.of("error", "Módulo não encontrado")).build();

        int sortOrder = body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99;
        String status = body.get("status") instanceof String s ? s : "ACTIVE";

        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "INSERT INTO platform_module_service_groups (module_id, name, description, icon_path, sort_order, status) " +
            "VALUES (CAST(:moduleId AS uuid), :name, :description, :iconPath, :sortOrder, :status) " +
            "RETURNING id::text, module_id::text, name, description, icon_path, sort_order, status, created_at::text, updated_at::text"
        )
        .setParameter("moduleId", moduleId).setParameter("name", name.trim())
        .setParameter("description", body.get("description")).setParameter("iconPath", body.get("icon_path"))
        .setParameter("sortOrder", sortOrder).setParameter("status", status)
        .getResultList();

        Object[] r = rows.get(0);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", r[0]); result.put("module_id", r[1]); result.put("name", r[2]);
        result.put("description", r[3]); result.put("icon_path", r[4]);
        result.put("sort_order", r[5]); result.put("status", r[6]);
        result.put("created_at", r[7]); result.put("updated_at", r[8]);
        result.put("service_count", 0);
        return Response.status(201).entity(result).build();
    }

    @PATCH
    @Path("/modules/{moduleId}/service-groups/{id}")
    @Transactional
    public Response updateModuleServiceGroup(
            @PathParam("moduleId") String moduleId,
            @PathParam("id") String id,
            Map<String, Object> body) {
        requireAdminPermission("admin.services.groups.manage");
        String name = (String) body.get("name");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();

        int updated = em.createNativeQuery(
            "UPDATE platform_module_service_groups SET name = :name, description = :description, " +
            "icon_path = :iconPath, sort_order = :sortOrder " +
            "WHERE id::text = :id AND module_id::text = :moduleId"
        )
        .setParameter("name", name.trim()).setParameter("description", body.get("description"))
        .setParameter("iconPath", body.get("icon_path"))
        .setParameter("sortOrder", body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99)
        .setParameter("id", id).setParameter("moduleId", moduleId)
        .executeUpdate();

        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Grupo não encontrado")).build();

        return Response.ok(Map.of("ok", true)).build();
    }

    @PATCH
    @Path("/modules/{moduleId}/service-groups/{id}/status")
    @Transactional
    public Response toggleModuleServiceGroupStatus(
            @PathParam("moduleId") String moduleId,
            @PathParam("id") String id,
            Map<String, Object> body) {
        requireAdminPermission("admin.services.groups.manage");
        String newStatus = body instanceof Map<?,?> ? (String) body.get("status") : null;
        if (!List.of("ACTIVE", "INACTIVE").contains(newStatus))
            return Response.status(400).entity(Map.of("error", "Status inválido. Use ACTIVE ou INACTIVE")).build();

        if ("INACTIVE".equals(newStatus)) {
            long activeCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM platform_module_services WHERE service_group_id::text = :id AND is_active = TRUE"
            ).setParameter("id", id).getSingleResult()).longValue();
            if (activeCount > 0)
                return Response.status(409).entity(Map.of(
                    "error", "Este grupo possui " + activeCount + " serviço(s) ativo(s). Remova ou mova-os antes de inativar.",
                    "active_service_count", activeCount
                )).build();
        }

        int updated = em.createNativeQuery(
            "UPDATE platform_module_service_groups SET status = :status " +
            "WHERE id::text = :id AND module_id::text = :moduleId"
        ).setParameter("status", newStatus).setParameter("id", id).setParameter("moduleId", moduleId).executeUpdate();
        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Grupo não encontrado")).build();
        return Response.ok(Map.of("ok", true, "status", newStatus)).build();
    }

    // ----------------------------------------------------------------
    // Módulos da versão do plano (plan_version_modules)
    // ----------------------------------------------------------------

    @GET
    @Path("/plans/{planId}/modules")
    public Response listPlanVersionModules(@PathParam("planId") String planId) {
        requireAdminPermission("admin.plans.view");
        return Response.ok(planService.listPlanVersionModules(planId)).build();
    }

    @POST
    @Path("/plans/{planId}/modules")
    public Response addPlanVersionModule(@PathParam("planId") String planId, Map<String, Object> body) {
        requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleRequest(body);
            return Response.status(201).entity(planService.addPlanVersionModule(planId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/plans/{planId}/modules/{pvmId}")
    public Response updatePlanVersionModule(
            @PathParam("planId") String planId,
            @PathParam("pvmId") String pvmId,
            Map<String, Object> body) {
        requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleRequest(body);
            return Response.ok(planService.updatePlanVersionModule(pvmId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/plans/{planId}/modules/{pvmId}")
    public Response removePlanVersionModule(
            @PathParam("planId") String planId,
            @PathParam("pvmId") String pvmId) {
        requireAdminPermission("admin.plans.edit");
        try {
            return Response.ok(planService.removePlanVersionModule(pvmId)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Limitações dos módulos do plano (plan_version_module_limits)
    // ----------------------------------------------------------------

    @POST
    @Path("/plans/{planId}/modules/{pvmId}/limits")
    public Response addPlanVersionModuleLimit(
            @PathParam("planId") String planId,
            @PathParam("pvmId") String pvmId,
            Map<String, Object> body) {
        requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleLimitRequest(body);
            return Response.status(201).entity(planService.addPlanVersionModuleLimit(pvmId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/plans/{planId}/modules/{pvmId}/limits/{limitId}")
    public Response updatePlanVersionModuleLimit(
            @PathParam("planId") String planId,
            @PathParam("pvmId") String pvmId,
            @PathParam("limitId") String limitId,
            Map<String, Object> body) {
        requireAdminPermission("admin.plans.edit");
        try {
            var req = mapToPlanVersionModuleLimitRequest(body);
            return Response.ok(planService.updatePlanVersionModuleLimit(limitId, req)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/plans/{planId}/modules/{pvmId}/limits/{limitId}")
    public Response removePlanVersionModuleLimit(
            @PathParam("planId") String planId,
            @PathParam("pvmId") String pvmId,
            @PathParam("limitId") String limitId) {
        requireAdminPermission("admin.plans.edit");
        try {
            return Response.ok(planService.removePlanVersionModuleLimit(limitId)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private long n(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private PlanService.PlanRequest mapToRequest(Map<String, Object> body) {
        return new PlanService.PlanRequest(
            (String) body.get("name"),
            (String) body.get("code"),
            (String) body.get("description"),
            body.get("price_monthly") != null ? new BigDecimal(body.get("price_monthly").toString()) : null,
            body.get("price_annual")  != null ? new BigDecimal(body.get("price_annual").toString())  : null,
            body.get("discount_annual_percent") != null ? ((Number) body.get("discount_annual_percent")).intValue() : null,
            body.get("max_users") != null ? ((Number) body.get("max_users")).intValue() : null,
            body.get("max_ai_requests_month") != null ? ((Number) body.get("max_ai_requests_month")).intValue() : null,
            (String) body.get("billing_type"),
            body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : null,
            (String) body.get("plan_type")
        );
    }

    private PlanService.PlanVersionModuleRequest mapToPlanVersionModuleRequest(Map<String, Object> body) {
        return new PlanService.PlanVersionModuleRequest(
            (String) body.get("module_id"),
            body.get("monthly_price")         != null ? new BigDecimal(body.get("monthly_price").toString())         : null,
            body.get("annual_monthly_price")  != null ? new BigDecimal(body.get("annual_monthly_price").toString())  : null,
            (String) body.get("status"),
            body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : null
        );
    }

    private PlanService.PlanVersionModuleLimitRequest mapToPlanVersionModuleLimitRequest(Map<String, Object> body) {
        return new PlanService.PlanVersionModuleLimitRequest(
            (String) body.get("title"),
            (String) body.get("description"),
            (String) body.get("limit_key"),
            (String) body.get("limit_value"),
            (String) body.get("unit"),
            body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : null
        );
    }

    @SuppressWarnings("unchecked")
    private List<PlanService.PlanModuleWithLimitsRequest> mapToPlanModuleWithLimitsRequests(Map<String, Object> body) {
        Object raw = body.get("modules");
        if (!(raw instanceof List<?> list)) return null;
        return list.stream().map(item -> {
            Map<String, Object> m = (Map<String, Object>) item;
            List<PlanService.PlanVersionModuleLimitRequest> limits = null;
            if (m.get("limits") instanceof List<?> ll) {
                limits = ll.stream().map(li -> {
                    Map<String, Object> l = (Map<String, Object>) li;
                    return new PlanService.PlanVersionModuleLimitRequest(
                        (String) l.get("title"),
                        (String) l.get("description"),
                        (String) l.get("limit_key"),
                        (String) l.get("limit_value"),
                        (String) l.get("unit"),
                        l.get("sort_order") != null ? ((Number) l.get("sort_order")).intValue() : null
                    );
                }).collect(java.util.stream.Collectors.toList());
            }
            return new PlanService.PlanModuleWithLimitsRequest(
                (String) m.get("module_id"),
                m.get("monthly_price")        != null ? new BigDecimal(m.get("monthly_price").toString())        : null,
                m.get("annual_monthly_price") != null ? new BigDecimal(m.get("annual_monthly_price").toString()) : null,
                (String) m.get("status"),
                m.get("sort_order") != null ? ((Number) m.get("sort_order")).intValue() : null,
                limits
            );
        }).collect(java.util.stream.Collectors.toList());
    }
}
