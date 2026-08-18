package com.saas.admin.dao;

import com.saas.admin.dto.PlatformSettingDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

@ApplicationScoped
public class PlatformSettingsDAO {

    @Inject
    EntityManager em;

    @SuppressWarnings("unchecked")
    public List<PlatformSettingDTO> findAll() {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT key, value, description, updated_at::text FROM platform_settings ORDER BY key"
        ).getResultList();

        return rows.stream()
            .map(row -> new PlatformSettingDTO((String) row[0], (String) row[1], (String) row[2], (String) row[3]))
            .toList();
    }

    public int updateValue(String key, String value, String userId) {
        return em.createNativeQuery(
            "UPDATE platform_settings SET value = :value, updated_at = NOW(), " +
            "updated_by_user_id = CAST(:userId AS uuid) WHERE key = :key"
        )
            .setParameter("value", value)
            .setParameter("userId", userId)
            .setParameter("key", key)
            .executeUpdate();
    }
}
