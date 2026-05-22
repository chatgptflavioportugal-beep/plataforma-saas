package com.saas.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class TenantSubscriptionRepository {

    @Inject
    EntityManager em;

    public record SubscriptionResult(
            UUID id,
            String status,
            String planCode,
            Set<String> moduleSlugSet
    ) {}

    public Optional<SubscriptionResult> findActiveByTenant(UUID tenantId) {
        var result = em.createNativeQuery(
                "SELECT ts.id, ts.status, p.code, " +
                "  (SELECT COALESCE(string_agg(pm.slug, ','), '') " +
                "   FROM plan_version_modules pvm " +
                "   JOIN platform_modules pm ON pm.id = pvm.module_id AND pm.is_active = true " +
                "   WHERE pvm.plan_id = p.id AND pvm.status = 'active') AS module_slugs " +
                "FROM tenant_subscriptions ts " +
                "JOIN plans p ON p.id = ts.plan_id " +
                "WHERE ts.tenant_id = :tenantId " +
                "AND ts.status NOT IN ('cancelled') " +
                "ORDER BY ts.created_at DESC LIMIT 1",
                Object[].class
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        if (result.isEmpty()) return Optional.empty();
        Object[] row = (Object[]) result.get(0);

        String slugsCsv = row[3] != null ? (String) row[3] : "";
        Set<String> moduleSlugSet = slugsCsv.isBlank()
                ? Set.of()
                : Arrays.stream(slugsCsv.split(",")).collect(Collectors.toSet());

        return Optional.of(new SubscriptionResult(
                (UUID) row[0],
                (String) row[1],
                (String) row[2],
                moduleSlugSet
        ));
    }
}
