package com.saas.profile.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class TenantSubscriptionDAO {

    @Inject
    EntityManager em;

    public record SubscriptionResult(
            UUID id,
            String status,
            String planCode,
            Set<String> moduleSlugSet
    ) {}

    public Optional<SubscriptionResult> findActiveByTenant(UUID tenantId) {
        var accountRows = em.createNativeQuery(
                "SELECT ts.id, ts.status, p.code " +
                "FROM tenant_subscriptions ts " +
                "JOIN plans p ON p.id = ts.plan_id " +
                "WHERE ts.tenant_id = :tenantId " +
                "AND ts.status NOT IN ('cancelled') " +
                "ORDER BY ts.created_at DESC LIMIT 1",
                Object[].class
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        if (accountRows.isEmpty()) return Optional.empty();
        Object[] row = (Object[]) accountRows.get(0);

        @SuppressWarnings("unchecked")
        List<String> slugRows = em.createNativeQuery(
                "SELECT DISTINCT pm.slug " +
                "FROM platform_modules pm " +
                "WHERE pm.is_active = TRUE " +
                "  AND EXISTS (" +
                "    SELECT 1 FROM profile_module_subscriptions pms " +
                "    WHERE pms.tenant_id = :tenantId " +
                "      AND pms.module_id = pm.id " +
                "      AND pms.status = 'ACTIVE'" +
                "      AND (pms.expires_at IS NULL OR pms.expires_at > NOW())" +
                "  )"
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        Set<String> moduleSlugSet = new HashSet<>(slugRows);

        return Optional.of(new SubscriptionResult(
                (UUID) row[0],
                (String) row[1],
                (String) row[2],
                moduleSlugSet
        ));
    }
}
