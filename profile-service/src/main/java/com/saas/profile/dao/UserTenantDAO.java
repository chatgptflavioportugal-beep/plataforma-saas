package com.saas.profile.dao;

import com.saas.profile.to.UserTenantMembershipTO;
import com.saas.profile.to.UserTenantTO;
import com.saas.platformdatabase.query.DatabaseQuery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserTenantDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    public List<UserTenantMembershipTO> findAllByUser(UUID userId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT ut.id::text, ut.user_id::text, ut.tenant_id::text, ut.role, ut.is_active,
                               t.name, t.slug, t.status, t.type, t.plan_id::text, t.trial_ends_at::text,
                               ut.created_at::text, ut.updated_at::text
                        FROM user_tenants ut JOIN tenants t ON t.id = ut.tenant_id
                        WHERE ut.user_id = :userId AND ut.is_active = TRUE
                        ORDER BY t.type ASC, ut.created_at ASC
                        """, UserTenantMembershipTO.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public Optional<UserTenantTO> findByUserAndTenant(UUID userId, UUID tenantId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT ut.tenant_id::text AS tenant_id, ut.role AS role FROM user_tenants ut
                        WHERE ut.user_id = :userId AND ut.tenant_id = :tenantId AND ut.is_active = TRUE
                        """, UserTenantTO.class)
                .setParameter("userId", userId)
                .setParameter("tenantId", tenantId)
                .getOptionalResult();
    }

    public Optional<UserTenantTO> findDefaultTenant(UUID userId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT ut.tenant_id::text AS tenant_id, ut.role AS role FROM user_tenants ut
                        WHERE ut.user_id = :userId AND ut.is_active = TRUE
                        ORDER BY ut.created_at ASC LIMIT 1
                        """, UserTenantTO.class)
                .setParameter("userId", userId)
                .getOptionalResult();
    }

    // ─── permissions_version — invalidação de PAT/MAT em cache no frontend ──────

    /** Incrementa a versão de um único membro (ex.: mudança de nível de acesso, remoção). */
    public void bumpVersionForMember(UUID userId, UUID tenantId) {
        em.createNativeQuery(
                "UPDATE user_tenants SET permissions_version = permissions_version + 1 " +
                "WHERE user_id = :userId AND tenant_id = :tenantId"
        )
        .setParameter("userId", userId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    }

    /** Incrementa a versão de todos os membros ativos de um nível de acesso (edição das permissões do nível). */
    public void bumpVersionForAccessLevel(UUID accessLevelId) {
        em.createNativeQuery(
                "UPDATE user_tenants SET permissions_version = permissions_version + 1 " +
                "WHERE access_level_id = :accessLevelId AND is_active = TRUE"
        )
        .setParameter("accessLevelId", accessLevelId)
        .executeUpdate();
    }
}
