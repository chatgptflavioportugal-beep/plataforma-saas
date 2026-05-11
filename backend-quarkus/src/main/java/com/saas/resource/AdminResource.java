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
        String role = jwt.getClaim("system_role");
        if (!"SUPER_ADMIN".equals(role)) {
            throw new jakarta.ws.rs.ForbiddenException("Acesso restrito a SUPER_ADMIN");
        }
    }

    @GET
    @Path("/stats")
    public Response stats(@Context SecurityContext ctx) {
        ensureSuperAdmin();

        long totalTenants = (Long) em.createNativeQuery("SELECT COUNT(*) FROM tenants").getSingleResult();
        long activeTenants = (Long) em.createNativeQuery("SELECT COUNT(*) FROM tenants WHERE status = 'active'").getSingleResult();
        long trialTenants = (Long) em.createNativeQuery("SELECT COUNT(*) FROM tenants WHERE status = 'trial'").getSingleResult();
        long suspendedTenants = (Long) em.createNativeQuery("SELECT COUNT(*) FROM tenants WHERE status = 'suspended'").getSingleResult();
        long totalUsers = (Long) em.createNativeQuery("SELECT COUNT(*) FROM user_profiles").getSingleResult();
        long totalPdfJobs = (Long) em.createNativeQuery("SELECT COUNT(*) FROM pdf_jobs").getSingleResult();

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
    public Response listPlans() {
        ensureSuperAdmin();
        var plans = em.createNativeQuery(
                "SELECT id, name, code, price_monthly, max_users, max_ai_requests_month, is_active " +
                "FROM plans ORDER BY sort_order",
                Object[].class
        ).getResultList();
        return Response.ok(plans).build();
    }

    @GET
    @Path("/users")
    public Response listUsers() {
        ensureSuperAdmin();
        var users = em.createNativeQuery(
                "SELECT up.id, au.email, up.full_name, up.system_role, up.is_active, " +
                "COUNT(ut.tenant_id) as tenant_count, up.created_at " +
                "FROM user_profiles up " +
                "JOIN auth.users au ON au.id = up.id " +
                "LEFT JOIN user_tenants ut ON ut.user_id = up.id AND ut.is_active = TRUE " +
                "GROUP BY up.id, au.email, up.full_name, up.system_role, up.is_active, up.created_at " +
                "ORDER BY up.created_at DESC",
                Object[].class
        ).getResultList();
        return Response.ok(users).build();
    }
}
