package com.saas.admin.dao;

import com.saas.admin.dto.PlatformSettingDTO;
import com.saas.admin.to.PlatformSettingTO;
import com.saas.platformdatabase.query.DatabaseQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

@ApplicationScoped
public class PlatformSettingsDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    public List<PlatformSettingDTO> findAll() {
        List<PlatformSettingTO> rows = databaseQuery
                .nativeQuery(em, "SELECT key, value, description, updated_at::text FROM platform_settings ORDER BY key",
                        PlatformSettingTO.class)
                .getResultList();

        return rows.stream()
            .map(row -> new PlatformSettingDTO(row.key(), row.value(), row.description(), row.updatedAt()))
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
