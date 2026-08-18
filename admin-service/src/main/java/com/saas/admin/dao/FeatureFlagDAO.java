package com.saas.admin.dao;

import com.saas.admin.dto.FeatureFlagDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FeatureFlagDAO {

    @Inject
    EntityManager em;

    @SuppressWarnings("unchecked")
    public List<FeatureFlagDTO> findAll() {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT id::text, key, name, description, is_enabled, created_at::text, updated_at::text " +
            "FROM feature_flags ORDER BY key"
        ).getResultList();

        return rows.stream()
            .map(row -> new FeatureFlagDTO(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (Boolean) row[4],
                (String) row[5],
                (String) row[6]))
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
