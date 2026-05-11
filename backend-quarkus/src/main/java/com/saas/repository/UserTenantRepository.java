package com.saas.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserTenantRepository {

    @Inject
    EntityManager em;

    public record UserTenantResult(UUID tenantId, String role) {}

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
