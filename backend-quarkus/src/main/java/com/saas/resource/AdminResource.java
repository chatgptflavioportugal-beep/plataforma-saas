package com.saas.resource;

import com.saas.service.PlanService;
import com.saas.service.TenantService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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

    private void ensureSuperAdmin() {
        String userId = jwt.getSubject();
        if (userId == null) throw new ForbiddenException("Acesso restrito a SUPER_ADMIN");
        try {
            String role = (String) em.createNativeQuery(
                    "SELECT system_role FROM user_profiles WHERE id::text = :id"
            ).setParameter("id", userId).getSingleResult();
            if (!"SUPER_ADMIN".equals(role)) throw new ForbiddenException("Acesso restrito a SUPER_ADMIN");
        } catch (jakarta.persistence.NoResultException e) {
            throw new ForbiddenException("Perfil de usuário não encontrado");
        }
    }

    // ----------------------------------------------------------------
    // Dashboard stats
    // ----------------------------------------------------------------

    @GET
    @Path("/stats")
    public Response stats(@Context SecurityContext ctx) {
        ensureSuperAdmin();
        long totalTenants     = n("SELECT COUNT(*) FROM tenants");
        long activeTenants    = n("SELECT COUNT(*) FROM tenants WHERE status = 'active'");
        long trialTenants     = n("SELECT COUNT(*) FROM tenants WHERE status = 'trial'");
        long suspendedTenants = n("SELECT COUNT(*) FROM tenants WHERE status = 'suspended'");
        long totalUsers       = n("SELECT COUNT(*) FROM user_profiles");
        long totalPdfJobs     = n("SELECT COUNT(*) FROM pdf_jobs");
        return Response.ok(Map.of(
                "total_tenants", totalTenants,
                "active_tenants", activeTenants,
                "trial_tenants", trialTenants,
                "suspended_tenants", suspendedTenants,
                "total_users", totalUsers,
                "total_pdf_jobs", totalPdfJobs
        )).build();
    }

    // ----------------------------------------------------------------
    // Tenants
    // ----------------------------------------------------------------

    @GET
    @Path("/tenants")
    public Response listTenants() {
        ensureSuperAdmin();
        return Response.ok(tenantService.listAdminTenants()).build();
    }

    // ----------------------------------------------------------------
    // Planos — listagem e histórico
    // ----------------------------------------------------------------

    @GET
    @Path("/plans")
    public Response listPlans() {
        ensureSuperAdmin();
        return Response.ok(planService.listAllPlansAdmin()).build();
    }

    @GET
    @Path("/plans/{code}/versions")
    public Response getPlanVersions(@PathParam("code") String code) {
        ensureSuperAdmin();
        return Response.ok(planService.getPlanVersionHistory(code)).build();
    }

    // ----------------------------------------------------------------
    // Planos — criar (v1)
    // ----------------------------------------------------------------

    @POST
    @Path("/plans")
    public Response createPlan(Map<String, Object> body) {
        ensureSuperAdmin();
        var req = mapToRequest(body);
        if (req.code() == null || req.code().isBlank())
            return Response.status(400).entity(Map.of("error", "code é obrigatório")).build();
        if (req.name() == null || req.name().isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();
        if (req.priceMonthly() == null)
            return Response.status(400).entity(Map.of("error", "price_monthly é obrigatório")).build();
        if (req.maxUsers() == null)
            return Response.status(400).entity(Map.of("error", "max_users é obrigatório")).build();
        if (req.maxAiRequestsMonth() == null)
            return Response.status(400).entity(Map.of("error", "max_ai_requests_month é obrigatório")).build();
        if (req.features() == null)
            return Response.status(400).entity(Map.of("error", "features é obrigatório")).build();

        return Response.status(201).entity(planService.createPlan(req)).build();
    }

    // ----------------------------------------------------------------
    // Planos — gerar nova versão (substitui edição direta)
    // ----------------------------------------------------------------

    @POST
    @Path("/plans/{id}/new-version")
    public Response createNewVersion(@PathParam("id") String id, Map<String, Object> body) {
        ensureSuperAdmin();
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
    // Planos — ativar / desativar
    // ----------------------------------------------------------------

    @PATCH
    @Path("/plans/{id}/status")
    @Consumes(MediaType.WILDCARD)
    public Response togglePlanStatus(@PathParam("id") String id) {
        ensureSuperAdmin();
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
        ensureSuperAdmin();
        try {
            return Response.ok(planService.setMostPopular(id)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    // ----------------------------------------------------------------
    // Usuários das Empresas (clientes — exclui roles administrativos)
    // ----------------------------------------------------------------

    private static final List<String> ADMIN_ROLES = List.of("SUPER_ADMIN", "ADMIN", "SUPPORT", "FINANCE");

    @GET
    @Path("/company-users")
    @SuppressWarnings("unchecked")
    public Response listCompanyUsers() {
        ensureSuperAdmin();
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
                "SELECT up.id::text, au.email, up.full_name, up.system_role, up.is_active, " +
                "COUNT(ut.tenant_id) AS tenant_count, up.created_at::text " +
                "FROM user_profiles up " +
                "JOIN auth.users au ON au.id = up.id " +
                "LEFT JOIN user_tenants ut ON ut.user_id = up.id AND ut.is_active = TRUE " +
                "WHERE up.system_role NOT IN ('SUPER_ADMIN', 'ADMIN', 'SUPPORT', 'FINANCE') " +
                "GROUP BY up.id, au.email, up.full_name, up.system_role, up.is_active, up.created_at " +
                "ORDER BY up.created_at DESC"
        ).getResultList();
        var users = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("email", row[1]);
            m.put("full_name", row[2]);
            m.put("system_role", row[3]);
            m.put("is_active", row[4]);
            m.put("tenant_count", row[5]);
            m.put("created_at", row[6]);
            return m;
        }).toList();
        return Response.ok(users).build();
    }

    // ----------------------------------------------------------------
    // Administradores do Sistema (somente roles administrativos)
    // ----------------------------------------------------------------

    @GET
    @Path("/system-admins")
    @SuppressWarnings("unchecked")
    public Response listSystemAdmins() {
        ensureSuperAdmin();
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
                "SELECT up.id::text, au.email, up.full_name, up.system_role, up.is_active, " +
                "up.created_at::text " +
                "FROM user_profiles up " +
                "JOIN auth.users au ON au.id = up.id " +
                "WHERE up.system_role IN ('SUPER_ADMIN', 'ADMIN', 'SUPPORT', 'FINANCE') " +
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
    // Assinaturas
    // ----------------------------------------------------------------

    @GET
    @Path("/subscriptions")
    @SuppressWarnings("unchecked")
    public Response listSubscriptions() {
        ensureSuperAdmin();
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
                "SELECT ts.id::text, t.name AS tenant_name, t.slug, " +
                "p.name AS plan_name, p.code AS plan_code, " +
                "ts.status, ts.billing_type, ts.plan_version, " +
                "ts.trial_start::text, ts.trial_end::text, " +
                "ts.current_period_start::text, ts.current_period_end::text, " +
                "ts.contracted_price_monthly, ts.contracted_price_annual, " +
                "ts.created_at::text " +
                "FROM tenant_subscriptions ts " +
                "JOIN tenants t ON t.id = ts.tenant_id " +
                "JOIN plans p ON p.id = ts.plan_id " +
                "ORDER BY ts.created_at DESC"
        ).getResultList();
        var subs = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("tenant_name", row[1]);
            m.put("slug", row[2]);
            m.put("plan_name", row[3]);
            m.put("plan_code", row[4]);
            m.put("status", row[5]);
            m.put("billing_type", row[6]);
            m.put("plan_version", row[7]);
            m.put("trial_start", row[8]);
            m.put("trial_end", row[9]);
            m.put("current_period_start", row[10]);
            m.put("current_period_end", row[11]);
            m.put("contracted_price_monthly", row[12]);
            m.put("contracted_price_annual", row[13]);
            m.put("created_at", row[14]);
            return m;
        }).toList();
        return Response.ok(subs).build();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private long n(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
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
            body.get("features") instanceof Map<?, ?> f ? (Map<String, Object>) f : null,
            (String) body.get("billing_type"),
            body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : null
        );
    }
}
