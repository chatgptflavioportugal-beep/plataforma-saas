package com.saas.profile.dao;

import com.saas.profile.to.AccessLevelPermissionTO;
import com.saas.profile.to.AccessLevelTO;
import com.saas.profile.to.ModuleServiceTO;
import com.saas.platformdatabase.query.DatabaseQuery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

/**
 * Persistência de níveis de acesso (papéis customizados por tenant): catálogo de
 * módulos/serviços disponíveis, os próprios níveis, e suas permissões (de serviço
 * e administrativas).
 */
@ApplicationScoped
public class AccessLevelDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    public List<ModuleServiceTO> findAvailableModuleTree(UUID tenantId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT pm.id::text AS module_id, pm.name AS module_name, pm.slug AS module_slug,
                          pm.icon_path AS module_icon_path,
                          CASE WHEN g.status = 'ACTIVE' THEN g.id::text ELSE NULL END AS group_id,
                          CASE WHEN g.status = 'ACTIVE' THEN g.name ELSE NULL END AS group_name,
                          CASE WHEN g.status = 'ACTIVE' THEN g.description ELSE NULL END AS group_description,
                          CASE WHEN g.status = 'ACTIVE' THEN g.icon_path ELSE NULL END AS group_icon_path,
                          CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE NULL END AS group_sort_order,
                          pms.id::text AS service_id, pms.name AS service_name, pms.slug AS service_slug,
                          pms.icon_path AS service_icon_path, pms.sort_order AS service_sort_order
                        FROM profile_module_subscriptions sub
                        JOIN platform_modules pm ON pm.id = sub.module_id
                        JOIN platform_module_services pms ON pms.module_id = pm.id
                        LEFT JOIN platform_module_service_groups g ON g.id = pms.service_group_id
                        WHERE sub.tenant_id = :tenantId AND sub.status = 'ACTIVE'
                          AND pm.is_active = TRUE AND pms.is_active = TRUE
                        ORDER BY pm.sort_order,
                          CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE 9999 END,
                          pms.sort_order
                        """, ModuleServiceTO.class)
                .setParameter("tenantId", tenantId)
                .getResultList();
    }

    public List<AccessLevelTO> findAllByTenant(UUID tenantId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT id::text, name, description, status, created_at::text, updated_at::text
                        FROM profile_access_levels
                        WHERE tenant_id = :tenantId
                        ORDER BY created_at ASC
                        """, AccessLevelTO.class)
                .setParameter("tenantId", tenantId)
                .getResultList();
    }

    public List<AccessLevelPermissionTO> findPermissions(UUID accessLevelId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT id::text, module_id::text, service_id::text
                        FROM profile_access_level_permissions
                        WHERE access_level_id = :levelId
                        """, AccessLevelPermissionTO.class)
                .setParameter("levelId", accessLevelId)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<String> findAdminPermissionKeys(UUID accessLevelId) {
        return (List<String>) em.createNativeQuery(
                "SELECT permission_key FROM profile_access_level_admin_permissions " +
                "WHERE access_level_id = :levelId ORDER BY created_at"
        ).setParameter("levelId", accessLevelId).getResultList();
    }

    public long countActiveMembers(UUID accessLevelId) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM user_tenants WHERE access_level_id = :levelId AND is_active = TRUE"
        ).setParameter("levelId", accessLevelId).getSingleResult()).longValue();
    }

    public long countPendingInvites(UUID accessLevelId) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM invitations WHERE access_level_id = :alId AND status = 'pending'"
        ).setParameter("alId", accessLevelId).getSingleResult()).longValue();
    }

    /** Insere o nível de acesso e devolve o id gerado (RETURNING evita um SELECT extra e a corrida entre inserts concorrentes). */
    public String insertAccessLevel(UUID tenantId, String name, String description) {
        return (String) em.createNativeQuery(
                "INSERT INTO profile_access_levels (tenant_id, name, description) " +
                "VALUES (:tenantId, :name, :description) RETURNING id::text"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("name", name)
        .setParameter("description", description)
        .getSingleResult().toString();
    }

    /** @return {@code true} se algum registro foi atualizado (nível existe e pertence ao tenant). */
    public boolean updateAccessLevel(UUID tenantId, UUID alId, String name, String description) {
        int updated = em.createNativeQuery(
                "UPDATE profile_access_levels " +
                "SET name = :name, description = :description, updated_at = NOW() " +
                "WHERE id = :alId AND tenant_id = :tenantId"
        )
        .setParameter("name", name)
        .setParameter("description", description)
        .setParameter("alId", alId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
        return updated > 0;
    }

    public boolean updateStatus(UUID tenantId, UUID alId, String status) {
        int updated = em.createNativeQuery(
                "UPDATE profile_access_levels " +
                "SET status = :status, updated_at = NOW() " +
                "WHERE id = :alId AND tenant_id = :tenantId"
        )
        .setParameter("status", status)
        .setParameter("alId", alId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
        return updated > 0;
    }

    public boolean deleteAccessLevel(UUID tenantId, UUID alId) {
        int deleted = em.createNativeQuery(
                "DELETE FROM profile_access_levels WHERE id = :alId AND tenant_id = :tenantId"
        )
        .setParameter("alId", alId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
        return deleted > 0;
    }

    public void deletePermissions(UUID alId) {
        em.createNativeQuery(
                "DELETE FROM profile_access_level_permissions WHERE access_level_id = :alId"
        ).setParameter("alId", alId).executeUpdate();
    }

    public void deleteAdminPermissions(UUID alId) {
        em.createNativeQuery(
                "DELETE FROM profile_access_level_admin_permissions WHERE access_level_id = :alId"
        ).setParameter("alId", alId).executeUpdate();
    }

    public boolean isServiceAvailableForTenant(UUID serviceId, UUID tenantId) {
        long valid = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM platform_module_services pms " +
                "JOIN profile_module_subscriptions sub ON sub.module_id = pms.module_id " +
                "WHERE pms.id = :serviceId AND sub.tenant_id = :tenantId " +
                "  AND sub.status = 'ACTIVE' AND pms.is_active = TRUE"
        ).setParameter("serviceId", serviceId).setParameter("tenantId", tenantId)
         .getSingleResult()).longValue();
        return valid > 0;
    }

    public String findModuleIdForService(UUID serviceId) {
        return (String) em.createNativeQuery(
                "SELECT module_id::text FROM platform_module_services WHERE id = :serviceId"
        ).setParameter("serviceId", serviceId).getSingleResult();
    }

    public void insertPermission(UUID levelId, UUID moduleId, UUID serviceId) {
        em.createNativeQuery(
                "INSERT INTO profile_access_level_permissions (access_level_id, module_id, service_id) " +
                "VALUES (:levelId, :moduleId, :serviceId) " +
                "ON CONFLICT (access_level_id, service_id) DO NOTHING"
        )
        .setParameter("levelId", levelId)
        .setParameter("moduleId", moduleId)
        .setParameter("serviceId", serviceId)
        .executeUpdate();
    }

    public void insertAdminPermission(UUID levelId, String key) {
        em.createNativeQuery(
                "INSERT INTO profile_access_level_admin_permissions (access_level_id, permission_key) " +
                "VALUES (:levelId, :key) ON CONFLICT (access_level_id, permission_key) DO NOTHING"
        )
        .setParameter("levelId", levelId)
        .setParameter("key", key)
        .executeUpdate();
    }

    public boolean hasAdminPermission(UUID userId, UUID tenantId, String permKey) {
        long count = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM profile_access_level_admin_permissions ap " +
                "JOIN user_tenants ut ON ut.access_level_id = ap.access_level_id " +
                "WHERE ut.user_id = :userId AND ut.tenant_id = :tenantId " +
                "  AND ut.is_active = TRUE AND ap.permission_key = :permKey"
        )
        .setParameter("userId", userId)
        .setParameter("tenantId", tenantId)
        .setParameter("permKey", permKey)
        .getSingleResult()).longValue();
        return count > 0;
    }
}
