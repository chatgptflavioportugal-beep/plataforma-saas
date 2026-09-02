package com.saas.admin.dao;

import com.saas.admin.dto.AdminUserCreatedDTO;
import com.saas.admin.dto.AdminUserDTO;
import com.saas.admin.to.AdminEmailTO;
import com.saas.admin.to.AdminUserTO;
import com.saas.admin.to.EmailRoleTO;
import com.saas.admin.to.ExistingUserTO;
import com.saas.platformdatabase.query.DatabaseQuery;
import com.saas.platformdatabase.query.NativeQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AdminUserDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    public List<AdminUserDTO> findAll(String search, String status, String accessLevelId) {
        StringBuilder sql = new StringBuilder(
            "SELECT up.id::text, au.email, up.full_name, up.system_role, up.is_active, " +
            "up.admin_access_level_id::text, al.name AS access_level_name, " +
            "up.created_at::text, au.last_sign_in_at::text " +
            "FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id " +
            "LEFT JOIN admin_access_levels al ON al.id = up.admin_access_level_id " +
            "WHERE up.system_role IN ('SUPER_ADMIN', 'ADMIN_USER')"
        );

        Map<String, Object> params = new LinkedHashMap<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(up.full_name) LIKE LOWER(:search) OR LOWER(au.email) LIKE LOWER(:search))");
            params.put("search", "%" + search.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(Boolean.parseBoolean(status) ? " AND up.is_active = TRUE" : " AND up.is_active = FALSE");
        }
        if (accessLevelId != null && !accessLevelId.isBlank()) {
            sql.append(" AND up.admin_access_level_id::text = :alId");
            params.put("alId", accessLevelId.trim());
        }
        sql.append(" ORDER BY up.system_role DESC, up.full_name");

        NativeQuery<AdminUserTO> query = databaseQuery.nativeQuery(em, sql.toString(), AdminUserTO.class);
        params.forEach(query::setParameter);
        List<AdminUserTO> rows = query.getResultList();

        return rows.stream().map(r -> new AdminUserDTO(
            r.id(), r.email(), r.fullName(), r.systemRole(), r.isActive(),
            r.adminAccessLevelId(), r.accessLevelName(), r.createdAt(), r.lastSignInAt()
        )).toList();
    }

    public long countActiveAccessLevel(String accessLevelId) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM admin_access_levels WHERE id::text = :id AND status = 'ACTIVE'"
        ).setParameter("id", accessLevelId).getSingleResult()).longValue();
    }

    public Optional<ExistingUserTO> findByEmail(String email) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT up.id::text, up.system_role FROM user_profiles up
                        JOIN auth.users au ON au.id = up.id WHERE au.email = :email
                        """, ExistingUserTO.class)
                .setParameter("email", email)
                .getOptionalResult();
    }

    public void promoteToAdmin(String userId, String fullName, String accessLevelId) {
        if (accessLevelId != null) {
            em.createNativeQuery(
                "UPDATE user_profiles SET system_role = 'ADMIN_USER', full_name = :name, " +
                "admin_access_level_id = CAST(:alId AS uuid), is_active = TRUE " +
                "WHERE id::text = :userId"
            ).setParameter("name", fullName)
             .setParameter("alId", accessLevelId)
             .setParameter("userId", userId)
             .executeUpdate();
        } else {
            em.createNativeQuery(
                "UPDATE user_profiles SET system_role = 'ADMIN_USER', full_name = :name, is_active = TRUE " +
                "WHERE id::text = :userId"
            ).setParameter("name", fullName)
             .setParameter("userId", userId)
             .executeUpdate();
        }
    }

    public Optional<AdminUserCreatedDTO> findCreatedById(String userId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT up.id::text, au.email, up.full_name, up.system_role, up.is_active,
                        up.admin_access_level_id::text, al.name AS access_level_name, up.created_at::text
                        FROM user_profiles up
                        JOIN auth.users au ON au.id = up.id
                        LEFT JOIN admin_access_levels al ON al.id = up.admin_access_level_id
                        WHERE up.id::text = :userId
                        """, AdminUserTO.class)
                .setParameter("userId", userId)
                .getOptionalResult()
                // tempPassword/emailSent são preenchidos pela camada de negócio, não fazem parte da consulta
                .map(r -> new AdminUserCreatedDTO(
                        r.id(), r.email(), r.fullName(), r.systemRole(), r.isActive(),
                        r.adminAccessLevelId(), r.accessLevelName(), r.createdAt(), null, null));
    }

    public long countSuperAdmin(String id) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM user_profiles WHERE id::text = :id AND system_role = 'SUPER_ADMIN'"
        ).setParameter("id", id).getSingleResult()).longValue();
    }

    public int updateProfileWithAccessLevel(String id, String fullName, String accessLevelId) {
        return em.createNativeQuery(
            "UPDATE user_profiles SET full_name = :name, admin_access_level_id = CAST(:alId AS uuid) " +
            "WHERE id::text = :id AND system_role = 'ADMIN_USER'"
        ).setParameter("name", fullName)
         .setParameter("alId", accessLevelId)
         .setParameter("id", id)
         .executeUpdate();
    }

    public int updateProfileClearingAccessLevel(String id, String fullName) {
        return em.createNativeQuery(
            "UPDATE user_profiles SET full_name = :name, admin_access_level_id = NULL " +
            "WHERE id::text = :id AND system_role = 'ADMIN_USER'"
        ).setParameter("name", fullName)
         .setParameter("id", id)
         .executeUpdate();
    }

    public int updateActiveStatus(String id, boolean isActive) {
        return em.createNativeQuery(
            "UPDATE user_profiles SET is_active = :isActive WHERE id::text = :id AND system_role = 'ADMIN_USER'"
        ).setParameter("isActive", isActive)
         .setParameter("id", id)
         .executeUpdate();
    }

    public Optional<EmailRoleTO> findEmailAndRole(String id) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT au.email, up.system_role FROM user_profiles up
                        JOIN auth.users au ON au.id = up.id WHERE up.id::text = :id
                        """, EmailRoleTO.class)
                .setParameter("id", id)
                .getOptionalResult();
    }

    public Optional<String> findAdminEmail(String id) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT au.email FROM user_profiles up
                        JOIN auth.users au ON au.id = up.id
                        WHERE up.id::text = :id AND up.system_role IN ('SUPER_ADMIN', 'ADMIN_USER')
                        """, AdminEmailTO.class)
                .setParameter("id", id)
                .getOptionalResult()
                .map(AdminEmailTO::email);
    }
}
