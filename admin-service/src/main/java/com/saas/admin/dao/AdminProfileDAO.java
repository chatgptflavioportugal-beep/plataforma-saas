package com.saas.admin.dao;

import com.saas.admin.to.AdminAuthProfileTO;
import com.saas.platformadmin.AdminProfile;
import com.saas.platformadmin.AdminProfileResolver;
import com.saas.platformadmin.AdminPermissionResolver;
import com.saas.platformdatabase.query.DatabaseQuery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

/** Consultas de user_profiles/admin_access_level_permissions para {@link com.saas.platformadmin.PlatformAdminAuthService}. */
@ApplicationScoped
public class AdminProfileDAO implements AdminProfileResolver, AdminPermissionResolver {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    @Override
    public Optional<AdminProfile> findProfile(UUID userId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT system_role, is_active, admin_access_level_id::text
                        FROM user_profiles WHERE id::text = :id
                        """, AdminAuthProfileTO.class)
                .setParameter("id", userId.toString())
                .getOptionalResult()
                .map(row -> new AdminProfile(
                        row.systemRole(),
                        Boolean.TRUE.equals(row.isActive()),
                        row.adminAccessLevelId() != null ? UUID.fromString(row.adminAccessLevelId()) : null
                ));
    }

    @Override
    public boolean hasPermission(UUID adminAccessLevelId, String permissionKey) {
        long has = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM admin_access_level_permissions " +
                "WHERE access_level_id::text = :lvl AND permission_key = :key"
        ).setParameter("lvl", adminAccessLevelId.toString()).setParameter("key", permissionKey)
         .getSingleResult()).longValue();
        return has > 0;
    }
}
