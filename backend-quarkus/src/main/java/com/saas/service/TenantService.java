package com.saas.service;

import com.saas.entity.Tenant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class TenantService {

    @Inject
    EntityManager em;

    @ConfigProperty(name = "trial.duration.days", defaultValue = "14")
    int trialDurationDays;

    @Transactional
    public Tenant createTenant(String name, String slug, UUID ownerId) {
        if (Tenant.findBySlug(slug) != null) {
            throw new IllegalArgumentException("Slug já em uso: " + slug);
        }

        UUID freePlanId = (UUID) em.createNativeQuery(
                "SELECT id FROM plans WHERE code = 'free'"
        ).getSingleResult();

        Tenant tenant = new Tenant();
        tenant.name = name;
        tenant.slug = slug;
        tenant.status = "trial";
        tenant.planId = freePlanId;
        tenant.trialEndsAt = OffsetDateTime.now().plusDays(trialDurationDays);
        tenant.persist();

        em.createNativeQuery(
                "INSERT INTO user_tenants (user_id, tenant_id, role) VALUES (:userId, :tenantId, 'owner')"
        )
        .setParameter("userId", ownerId)
        .setParameter("tenantId", tenant.id)
        .executeUpdate();

        em.createNativeQuery(
                "INSERT INTO tenant_subscriptions (tenant_id, plan_id, status, trial_start, trial_end) " +
                "VALUES (:tenantId, :planId, 'trial', NOW(), :trialEnd)"
        )
        .setParameter("tenantId", tenant.id)
        .setParameter("planId", freePlanId)
        .setParameter("trialEnd", tenant.trialEndsAt)
        .executeUpdate();

        return tenant;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getTenantContext(UUID tenantId) {
        var rows = em.createNativeQuery(
                "SELECT t.id, t.name, t.slug, t.status, t.trial_ends_at, " +
                "ts.status as sub_status, ts.trial_end, " +
                "p.id as plan_id, p.name as plan_name, p.code as plan_code, " +
                "p.price_monthly, p.max_users, p.max_ai_requests_month, p.features, " +
                "ut.role " +
                "FROM tenants t " +
                "JOIN tenant_subscriptions ts ON ts.tenant_id = t.id " +
                "JOIN plans p ON p.id = ts.plan_id " +
                "LEFT JOIN user_tenants ut ON ut.tenant_id = t.id " +
                "WHERE t.id = :tenantId " +
                "ORDER BY ts.created_at DESC LIMIT 1",
                Object[].class
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        if (rows.isEmpty()) throw new jakarta.ws.rs.NotFoundException("Tenant não encontrado");

        Object[] row = (Object[]) rows.get(0);
        return Map.of(
                "tenant", Map.of("id", row[0], "name", row[1], "slug", row[2], "status", row[3]),
                "subscription", Map.of("status", row[5], "trial_end", row[6]),
                "plan", Map.of("id", row[7], "name", row[8], "code", row[9],
                               "price_monthly", row[10], "max_users", row[11]),
                "role", row[14]
        );
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listAdminTenants() {
        return em.createNativeQuery(
                "SELECT t.id, t.name, t.slug, t.status, t.created_at, " +
                "p.name as plan_name, ts.status as sub_status, " +
                "COUNT(ut.user_id) as user_count " +
                "FROM tenants t " +
                "LEFT JOIN tenant_subscriptions ts ON ts.tenant_id = t.id " +
                "LEFT JOIN plans p ON p.id = ts.plan_id " +
                "LEFT JOIN user_tenants ut ON ut.tenant_id = t.id AND ut.is_active = TRUE " +
                "GROUP BY t.id, t.name, t.slug, t.status, t.created_at, p.name, ts.status " +
                "ORDER BY t.created_at DESC",
                Object[].class
        ).getResultList();
    }
}
