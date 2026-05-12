package com.saas.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserTenantRepository {

    @Inject
    EntityManager em;

    public record UserTenantResult(UUID tenantId, String role) {}

    public record UserTenantRow(
        String id,
        String userId,
        String tenantId,
        String role,
        boolean isActive,
        String tenantName,
        String tenantSlug,
        String tenantStatus,
        String planId,
        String trialEndsAt,
        String createdAt,
        String updatedAt
    ) {}

    @SuppressWarnings("unchecked")
    public List<UserTenantRow> findAllByUser(UUID userId) {
        var rows = em.createNativeQuery(
            "SELECT ut.id, ut.user_id, ut.tenant_id, ut.role, ut.is_active, " +
            "t.name, t.slug, t.status, t.plan_id, t.trial_ends_at, ut.created_at, ut.updated_at " +
            "FROM user_tenants ut JOIN tenants t ON t.id = ut.tenant_id " +
            "WHERE ut.user_id = :userId AND ut.is_active = TRUE " +
            "ORDER BY ut.created_at ASC",
            Object[].class
        )
        .setParameter("userId", userId)
        .getResultList();

        return rows.stream().map(r -> {
            Object[] row = (Object[]) r;
            return new UserTenantRow(
                row[0].toString(),
                row[1].toString(),
                row[2].toString(),
                (String) row[3],
                (Boolean) row[4],
                (String) row[5],
                (String) row[6],
                (String) row[7],
                row[8] != null ? row[8].toString() : null,
                row[9] != null ? row[9].toString() : null,
                row[10].toString(),
                row[11].toString()
            );
        }).toList();
    }

    public Optional<UserTenantResult> findByUserAndTenant(UUID userId, UUID tenantId) {
        var result = em.createNativeQuery(
                "SELECT ut.tenant_id, ut.role FROM user_tenants ut " +
                "WHERE ut.user_id = :userId AND ut.tenant_id = :tenantId AND ut.is_active = TRUE",
                Object[].class
        )
        .setParameter("userId", userId)
        .setParameter("tenantId", tenantId)
        .getResultList();

        if (result.isEmpty()) return Optional.empty();
        Object[] row = (Object[]) result.get(0);
        return Optional.of(new UserTenantResult((UUID) row[0], (String) row[1]));
    }

    public Optional<UserTenantResult> findDefaultTenant(UUID userId) {
        var result = em.createNativeQuery(
                "SELECT ut.tenant_id, ut.role FROM user_tenants ut " +
                "WHERE ut.user_id = :userId AND ut.is_active = TRUE " +
                "ORDER BY ut.created_at ASC LIMIT 1",
                Object[].class
        )
        .setParameter("userId", userId)
        .getResultList();

        if (result.isEmpty()) return Optional.empty();
        Object[] row = (Object[]) result.get(0);
        return Optional.of(new UserTenantResult((UUID) row[0], (String) row[1]));
    }

    public long countByTenant(UUID tenantId) {
        return (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM user_tenants WHERE tenant_id = :tenantId AND is_active = TRUE"
        )
        .setParameter("tenantId", tenantId)
        .getSingleResult();
    }
}
