package com.saas.catalog.dao;

import com.saas.catalog.to.UserTenantTO;
import com.saas.platformdatabase.query.DatabaseQuery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserTenantDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

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
}
