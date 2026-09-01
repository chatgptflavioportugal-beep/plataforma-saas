package com.saas.profile.dao;

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

    public record ModuleServiceRow(
            String moduleId, String moduleName, String moduleSlug, String moduleIconPath,
            String groupId, String groupName, String groupDescription, String groupIconPath, Integer groupSortOrder,
            String serviceId, String serviceName, String serviceSlug, String serviceIconPath, Integer serviceSortOrder
    ) {}

    public record AccessLevelRow(
            String id, String name, String description, String status, String createdAt, String updatedAt
    ) {}

    public record AccessLevelPermissionRow(String id, String moduleId, String serviceId) {}

    @SuppressWarnings("unchecked")
    public List<ModuleServiceRow> findAvailableModuleTree(UUID tenantId) {
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
                "SELECT pm.id::text, pm.name, pm.slug, pm.icon_path, " +
                "  CASE WHEN g.status = 'ACTIVE' THEN g.id::text ELSE NULL END, " +
                "  CASE WHEN g.status = 'ACTIVE' THEN g.name ELSE NULL END, " +
                "  CASE WHEN g.status = 'ACTIVE' THEN g.description ELSE NULL END, " +
                "  CASE WHEN g.status = 'ACTIVE' THEN g.icon_path ELSE NULL END, " +
                "  CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE NULL END, " +
                "  pms.id::text, pms.name, pms.slug, pms.icon_path, pms.sort_order " +
                "FROM profile_module_subscriptions sub " +
                "JOIN platform_modules pm ON pm.id = sub.module_id " +
                "JOIN platform_module_services pms ON pms.module_id = pm.id " +
                "LEFT JOIN platform_module_service_groups g ON g.id = pms.service_group_id " +
                "WHERE sub.tenant_id = :tenantId AND sub.status = 'ACTIVE' " +
                "  AND pm.is_active = TRUE AND pms.is_active = TRUE " +
                "ORDER BY pm.sort_order, " +
                "  CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE 9999 END, " +
                "  pms.sort_order"
        ).setParameter("tenantId", tenantId).getResultList();

        return rows.stream().map(r -> new ModuleServiceRow(
                (String) r[0], (String) r[1], (String) r[2], (String) r[3],
                (String) r[4], (String) r[5], (String) r[6], (String) r[7], (Integer) r[8],
                (String) r[9], (String) r[10], (String) r[11], (String) r[12], (Integer) r[13]
        )).toList();
    }

    @SuppressWarnings("unchecked")
    public List<AccessLevelRow> findAllByTenant(UUID tenantId) {
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
                "SELECT id::text, name, description, status, created_at::text, updated_at::text " +
                "FROM profile_access_levels " +
                "WHERE tenant_id = :tenantId " +
                "ORDER BY created_at ASC"
        ).setParameter("tenantId", tenantId).getResultList();

        return rows.stream().map(r -> new AccessLevelRow(
                (String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5]
        )).toList();
    }

    @SuppressWarnings("unchecked")
    public List<AccessLevelPermissionRow> findPermissions(UUID accessLevelId) {
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
                "SELECT id::text, module_id::text, service_id::text " +
                "FROM profile_access_level_permissions " +
                "WHERE access_level_id = :levelId"
        ).setParameter("levelId", accessLevelId).getResultList();

        return rows.stream().map(r -> new AccessLevelPermissionRow((String) r[0], (String) r[1], (String) r[2])).toList();
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
