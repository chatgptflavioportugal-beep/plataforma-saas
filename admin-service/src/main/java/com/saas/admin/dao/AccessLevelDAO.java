package com.saas.admin.dao;

import com.saas.admin.dto.AccessLevelDTO;
import com.saas.admin.dto.AccessLevelDetailDTO;
import com.saas.admin.to.AccessLevelBasicTO;
import com.saas.admin.to.AccessLevelSummaryTO;
import com.saas.platformdatabase.query.DatabaseQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AccessLevelDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    /**
     * Retorna o admin_access_level_id (pode conter null) do usuário, ou
     * Optional.empty() se não houver linha em user_profiles para o id.
     */
    @SuppressWarnings("unchecked")
    public Optional<String> findAccessLevelIdForUser(String userId) {
        List<String> ids = em.createNativeQuery(
            "SELECT admin_access_level_id::text FROM user_profiles WHERE id::text = :id"
        ).setParameter("id", userId).getResultList();
        return ids.isEmpty() ? Optional.empty() : Optional.ofNullable(ids.get(0));
    }

    @SuppressWarnings("unchecked")
    public List<String> findPermissionKeys(String accessLevelId) {
        return em.createNativeQuery(
            "SELECT permission_key FROM admin_access_level_permissions " +
            "WHERE access_level_id::text = :id ORDER BY permission_key"
        ).setParameter("id", accessLevelId).getResultList();
    }

    public List<AccessLevelDTO> findAll(String status) {
        String normalizedStatus = (status != null && !status.isBlank()) ? status.toUpperCase().trim() : null;

        List<AccessLevelSummaryTO> rows = databaseQuery
                .nativeQuery(em, """
                        SELECT al.id::text, al.name, al.description, al.status,
                        al.created_at::text, al.updated_at::text,
                        (SELECT COUNT(*) FROM admin_access_level_permissions p WHERE p.access_level_id = al.id)::int AS perm_count,
                        (SELECT COUNT(*) FROM user_profiles up WHERE up.admin_access_level_id = al.id AND up.system_role = 'ADMIN_USER')::int AS user_count
                        FROM admin_access_levels al WHERE (:status IS NULL OR al.status = :status)
                        ORDER BY al.name
                        """, AccessLevelSummaryTO.class)
                .setParameter("status", normalizedStatus)
                .getResultList();

        return rows.stream()
            .map(r -> new AccessLevelDTO(
                r.id(), r.name(), r.description(), r.status(),
                r.createdAt(), r.updatedAt(), r.permCount(), r.userCount()))
            .toList();
    }

    public Optional<AccessLevelDetailDTO> findById(String id, List<String> permissionKeys) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT id::text, name, description, status, created_at::text, updated_at::text
                        FROM admin_access_levels WHERE id::text = :id
                        """, AccessLevelBasicTO.class)
                .setParameter("id", id)
                .getOptionalResult()
                .map(r -> new AccessLevelDetailDTO(
                        r.id(), r.name(), r.description(), r.status(),
                        r.createdAt(), r.updatedAt(), permissionKeys));
    }

    public AccessLevelDetailDTO insert(String name, String description, String status, List<String> permissionKeys) {
        AccessLevelBasicTO r = databaseQuery
                .nativeQuery(em, """
                        INSERT INTO admin_access_levels (name, description, status)
                        VALUES (:name, :description, :status)
                        RETURNING id::text, name, description, status, created_at::text, updated_at::text
                        """, AccessLevelBasicTO.class)
                .setParameter("name", name)
                .setParameter("description", description)
                .setParameter("status", status)
                .getOptionalResult()
                .orElseThrow();

        return new AccessLevelDetailDTO(
            r.id(), r.name(), r.description(), r.status(),
            r.createdAt(), r.updatedAt(), permissionKeys);
    }

    public int updateNameDescription(String id, String name, String description) {
        return em.createNativeQuery(
            "UPDATE admin_access_levels SET name = :name, description = :description WHERE id::text = :id"
        ).setParameter("name", name)
         .setParameter("description", description)
         .setParameter("id", id)
         .executeUpdate();
    }

    public void deletePermissions(String accessLevelId) {
        em.createNativeQuery(
            "DELETE FROM admin_access_level_permissions WHERE access_level_id::text = :id"
        ).setParameter("id", accessLevelId).executeUpdate();
    }

    public void savePermissions(String accessLevelId, List<String> permKeys) {
        for (String key : permKeys) {
            em.createNativeQuery(
                "INSERT INTO admin_access_level_permissions (access_level_id, permission_key) " +
                "VALUES (CAST(:levelId AS uuid), :key) ON CONFLICT DO NOTHING"
            ).setParameter("levelId", accessLevelId).setParameter("key", key).executeUpdate();
        }
    }

    public long countActiveAdminUsers(String accessLevelId) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM user_profiles WHERE admin_access_level_id::text = :id " +
            "AND system_role = 'ADMIN_USER' AND is_active = TRUE"
        ).setParameter("id", accessLevelId).getSingleResult()).longValue();
    }

    public int updateStatus(String id, String status) {
        return em.createNativeQuery(
            "UPDATE admin_access_levels SET status = :status WHERE id::text = :id"
        ).setParameter("status", status).setParameter("id", id).executeUpdate();
    }
}
