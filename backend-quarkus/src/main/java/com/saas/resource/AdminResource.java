package com.saas.resource;

import com.saas.service.TenantService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Map;

@Path("/api/v1/admin")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    TenantService tenantService;

    @Inject
    EntityManager em;

    @Inject
    JsonWebToken jwt;

    private void ensureSuperAdmin() {
        String userId = jwt.getSubject();
        if (userId == null) {
            throw new jakarta.ws.rs.ForbiddenException("Acesso restrito a SUPER_ADMIN");
        }
        try {
            String role = (String) em.createNativeQuery(
                    "SELECT system_role FROM user_profiles WHERE id::text = :id"
            ).setParameter("id", userId).getSingleResult();
            if (!"SUPER_ADMIN".equals(role)) {
                throw new jakarta.ws.rs.ForbiddenException("Acesso restrito a SUPER_ADMIN");
            }
        } catch (jakarta.persistence.NoResultException e) {
            throw new jakarta.ws.rs.ForbiddenException("Perfil de usuário não encontrado");
        }
    }

    @GET
    @Path("/stats")
    public Response stats(@Context SecurityContext ctx) {
        ensureSuperAdmin();

        long totalTenants = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM tenants").getSingleResult()).longValue();
        long activeTenants = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM tenants WHERE status = 'active'").getSingleResult()).longValue();
        long trialTenants = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM tenants WHERE status = 'trial'").getSingleResult()).longValue();
        long suspendedTenants = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM tenants WHERE status = 'suspended'").getSingleResult()).longValue();
        long totalUsers = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM user_profiles").getSingleResult()).longValue();
        long totalPdfJobs = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM pdf_jobs").getSingleResult()).longValue();

        return Response.ok(Map.of(
                "total_tenants", totalTenants,
                "active_tenants", activeTenants,
                "trial_tenants", trialTenants,
                "suspended_tenants", suspendedTenants,
                "total_users", totalUsers,
                "total_pdf_jobs", totalPdfJobs
        )).build();
    }

    @GET
    @Path("/tenants")
    public Response listTenants() {
        ensureSuperAdmin();
        return Response.ok(tenantService.listAdminTenants()).build();
    }

    @GET
    @Path("/plans")
    @SuppressWarnings("unchecked")
    public Response listPlans() {
        ensureSuperAdmin();
        List<Object[]> rows = (java.util.List<Object[]>) em.createNativeQuery(
                "SELECT id::text, name, code, price_monthly, max_users, max_ai_requests_month, is_active " +
                "FROM plans ORDER BY sort_order"
        ).getResultList();
        var plans = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            m.put("code", row[2]);
            m.put("price_monthly", row[3]);
            m.put("max_users", row[4]);
            m.put("max_ai_requests_month", row[5]);
            m.put("is_active", row[6]);
            return m;
        }).toList();
        return Response.ok(plans).build();
    }

    @GET
    @Path("/users")
    @SuppressWarnings("unchecked")
    public Response listUsers() {
        ensureSuperAdmin();
        List<Object[]> rows = (java.util.List<Object[]>) em.createNativeQuery(
                "SELECT up.id::text, au.email, up.full_name, up.system_role, up.is_active, " +
                "COUNT(ut.tenant_id) as tenant_count, up.created_at::text " +
                "FROM user_profiles up " +
                "JOIN auth.users au ON au.id = up.id " +
                "LEFT JOIN user_tenants ut ON ut.user_id = up.id AND ut.is_active = TRUE " +
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
}
