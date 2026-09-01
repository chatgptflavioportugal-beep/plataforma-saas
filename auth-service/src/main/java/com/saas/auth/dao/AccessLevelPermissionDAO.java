package com.saas.auth.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AccessLevelPermissionDAO {

    @Inject
    EntityManager em;

    public record AccessLevelPermission(String accessLevelId, String permissionKey) {}

    /**
     * Nível de acesso do vínculo usuário-tenant e as permissões administrativas
     * (profile.*) associadas a ele. Uma linha por permissão; se o nível de acesso
     * não tiver nenhuma permissão cadastrada, retorna uma única linha com
     * permissionKey nulo (LEFT JOIN) apenas para expor o accessLevelId.
     */
    @SuppressWarnings("unchecked")
    public List<AccessLevelPermission> findAdminPermissions(UUID userId, UUID tenantId) {
        List<Object[]> rows = em.createNativeQuery("""
            SELECT ut.access_level_id::text,
                   ap.permission_key
            FROM user_tenants ut
            LEFT JOIN profile_access_level_admin_permissions ap
              ON ap.access_level_id = ut.access_level_id
            WHERE ut.user_id = :userId
              AND ut.tenant_id = :tenantId
              AND ut.is_active = TRUE
        """)
        .setParameter("userId", userId)
        .setParameter("tenantId", tenantId)
        .getResultList();

        List<AccessLevelPermission> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new AccessLevelPermission((String) row[0], (String) row[1]));
        }
        return result;
    }
}
