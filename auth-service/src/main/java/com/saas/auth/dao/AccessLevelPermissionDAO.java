package com.saas.auth.dao;

import com.saas.auth.to.AccessLevelPermissionTO;
import com.saas.platformdatabase.query.DatabaseQuery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AccessLevelPermissionDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    /**
     * Nível de acesso do vínculo usuário-tenant e as permissões administrativas
     * (profile.*) associadas a ele. Uma linha por permissão; se o nível de acesso
     * não tiver nenhuma permissão cadastrada, retorna uma única linha com
     * permissionKey nulo (LEFT JOIN) apenas para expor o accessLevelId.
     */
    public List<AccessLevelPermissionTO> findAdminPermissions(UUID userId, UUID tenantId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT ut.access_level_id::text,
                               ap.permission_key
                        FROM user_tenants ut
                        LEFT JOIN profile_access_level_admin_permissions ap
                          ON ap.access_level_id = ut.access_level_id
                        WHERE ut.user_id = :userId
                          AND ut.tenant_id = :tenantId
                          AND ut.is_active = TRUE
                        """, AccessLevelPermissionTO.class)
                .setParameter("userId", userId)
                .setParameter("tenantId", tenantId)
                .getResultList();
    }
}
