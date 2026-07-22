package com.saas.subscription.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserTenantRepository {

    @Inject
    EntityManager em;

    public record UserTenantResult(UUID tenantId, String role) {}

    @SuppressWarnings("unchecked")
    public Optional<UserTenantResult> findByUserAndTenant(UUID userId, UUID tenantId) {
        var result = (java.util.List<Object[]>) em.createNativeQuery(
                "SELECT ut.tenant_id::text, ut.role FROM user_tenants ut " +
                "WHERE ut.user_id = :userId AND ut.tenant_id = :tenantId AND ut.is_active = TRUE"
        )
        .setParameter("userId", userId)
        .setParameter("tenantId", tenantId)
        .getResultList();

        if (result.isEmpty()) return Optional.empty();
        Object[] row = result.get(0);
        return Optional.of(new UserTenantResult(UUID.fromString((String) row[0]), (String) row[1]));
    }

    @SuppressWarnings("unchecked")
    public Optional<UserTenantResult> findDefaultTenant(UUID userId) {
        var result = (java.util.List<Object[]>) em.createNativeQuery(
                "SELECT ut.tenant_id::text, ut.role FROM user_tenants ut " +
                "WHERE ut.user_id = :userId AND ut.is_active = TRUE " +
                "ORDER BY ut.created_at ASC LIMIT 1"
        )
        .setParameter("userId", userId)
        .getResultList();

        if (result.isEmpty()) return Optional.empty();
        Object[] row = result.get(0);
        return Optional.of(new UserTenantResult(UUID.fromString((String) row[0]), (String) row[1]));
    }

    /** Incrementa a versão de todos os membros ativos de um tenant (mudança de assinatura/plano). */
    public void bumpVersionForTenant(UUID tenantId) {
        em.createNativeQuery(
                "UPDATE user_tenants SET permissions_version = permissions_version + 1 " +
                "WHERE tenant_id = :tenantId AND is_active = TRUE"
        )
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    }
}
