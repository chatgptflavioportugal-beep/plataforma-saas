package com.saas.admin.dao;

import com.saas.admin.dto.AdminUserCreatedDTO;
import com.saas.admin.dto.AdminUserDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AdminUserDAO {

    @Inject
    EntityManager em;

    public record ExistingUserRow(String id, String systemRole) {
    }

    @SuppressWarnings("unchecked")
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

        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(r -> new AdminUserDTO(
            (String) r[0], (String) r[1], (String) r[2], (String) r[3], (Boolean) r[4],
            (String) r[5], (String) r[6], (String) r[7], (String) r[8]
        )).toList();
    }

    public long countActiveAccessLevel(String accessLevelId) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM admin_access_levels WHERE id::text = :id AND status = 'ACTIVE'"
        ).setParameter("id", accessLevelId).getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    public Optional<ExistingUserRow> findByEmail(String email) {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT up.id::text, up.system_role FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id WHERE au.email = :email"
        ).setParameter("email", email).getResultList();

        if (rows.isEmpty()) return Optional.empty();
        Object[] row = rows.get(0);
        return Optional.of(new ExistingUserRow((String) row[0], (String) row[1]));
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

    @SuppressWarnings("unchecked")
    public Optional<AdminUserCreatedDTO> findCreatedById(String userId) {
        List<Object[]> result = em.createNativeQuery(
            "SELECT up.id::text, au.email, up.full_name, up.system_role, up.is_active, " +
            "up.admin_access_level_id::text, al.name, up.created_at::text " +
            "FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id " +
            "LEFT JOIN admin_access_levels al ON al.id = up.admin_access_level_id " +
            "WHERE up.id::text = :userId"
        ).setParameter("userId", userId).getResultList();

        if (result.isEmpty()) return Optional.empty();
        Object[] r = result.get(0);
        // tempPassword/emailSent são preenchidos pela camada de negócio, não fazem parte da consulta
        return Optional.of(new AdminUserCreatedDTO(
            (String) r[0], (String) r[1], (String) r[2], (String) r[3], (Boolean) r[4],
            (String) r[5], (String) r[6], (String) r[7], null, null));
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

    public record EmailRoleRow(String email, String systemRole) {
    }

    @SuppressWarnings("unchecked")
    public Optional<EmailRoleRow> findEmailAndRole(String id) {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT au.email, up.system_role FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id WHERE up.id::text = :id"
        ).setParameter("id", id).getResultList();

        if (rows.isEmpty()) return Optional.empty();
        Object[] row = rows.get(0);
        return Optional.of(new EmailRoleRow((String) row[0], (String) row[1]));
    }

    @SuppressWarnings("unchecked")
    public Optional<String> findAdminEmail(String id) {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT au.email FROM user_profiles up " +
            "JOIN auth.users au ON au.id = up.id " +
            "WHERE up.id::text = :id AND up.system_role IN ('SUPER_ADMIN', 'ADMIN_USER')"
        ).setParameter("id", id).getResultList();

        return rows.isEmpty() ? Optional.empty() : Optional.of((String) rows.get(0)[0]);
    }
}
