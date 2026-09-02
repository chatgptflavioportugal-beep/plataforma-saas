package com.saas.admin.dao;

import com.saas.admin.dto.FeatureFlagDTO;
import com.saas.admin.to.FeatureFlagTO;
import com.saas.platformdatabase.query.DatabaseQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FeatureFlagDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    public List<FeatureFlagDTO> findAll() {
        List<FeatureFlagTO> rows = databaseQuery
                .nativeQuery(em, "SELECT id::text, key, name, description, is_enabled, created_at::text, updated_at::text " +
                        "FROM feature_flags ORDER BY key", FeatureFlagTO.class)
                .getResultList();

        return rows.stream()
            .map(row -> new FeatureFlagDTO(
                row.id(), row.key(), row.name(), row.description(),
                row.isEnabled(), row.createdAt(), row.updatedAt()))
            .toList();
    }

    public boolean existsByKey(String key) {
        long count = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM feature_flags WHERE key = :key"
        ).setParameter("key", key).getSingleResult()).longValue();
        return count > 0;
    }

    public void insert(UUID id, String key, String name, String description, boolean isEnabled, String userId) {
        em.createNativeQuery(
            "INSERT INTO feature_flags (id, key, name, description, is_enabled, updated_by_user_id) " +
            "VALUES (:id, :key, :name, :description, :isEnabled, CAST(:userId AS uuid))"
        )
            .setParameter("id", id)
            .setParameter("key", key)
            .setParameter("name", name)
            .setParameter("description", description)
            .setParameter("isEnabled", isEnabled)
            .setParameter("userId", userId)
            .executeUpdate();
    }

    public int updateNameDescription(String id, String name, String description, String userId) {
        return em.createNativeQuery(
            "UPDATE feature_flags SET name = :name, description = :description, updated_at = NOW(), " +
            "updated_by_user_id = CAST(:userId AS uuid) WHERE id::text = :id"
        )
            .setParameter("name", name)
            .setParameter("description", description)
            .setParameter("userId", userId)
            .setParameter("id", id)
            .executeUpdate();
    }

    public int toggleStatus(String id, String userId) {
        return em.createNativeQuery(
            "UPDATE feature_flags SET is_enabled = NOT is_enabled, updated_at = NOW(), " +
            "updated_by_user_id = CAST(:userId AS uuid) WHERE id::text = :id"
        ).setParameter("userId", userId).setParameter("id", id).executeUpdate();
    }
}
