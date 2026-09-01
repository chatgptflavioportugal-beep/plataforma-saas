package com.saas.admin.negocio.impl;

import com.saas.admin.dao.AdminUserDAO;
import com.saas.admin.dto.AdminUserCreatedDTO;
import com.saas.admin.dto.AdminUserDTO;

import java.util.List;
import java.util.Optional;

/**
 * Gestão de usuários administrativos (system_role SUPER_ADMIN/ADMIN_USER).
 * Persistência isolada em AdminUserDAO; comunicação com a Admin API
 * do Supabase isolada em SupabaseAdminRepository.
 */
public interface AdminUsersNegocio {

    List<AdminUserDTO> list(String search, String status, String accessLevelId);

    boolean isAccessLevelActive(String accessLevelId);

    Optional<AdminUserDAO.ExistingUserRow> findExistingByEmail(String normalizedEmail);

    boolean isSupabaseConfigured();

    String createSupabaseUser(String email, String fullName, String password);

    Optional<AdminUserCreatedDTO> finalizeCreate(
            String newUserId, String fullNameTrimmed, String accessLevelId, String normalizedEmail,
            String effectivePassword, boolean sendPasswordEmail, String actorUserId);

    boolean isSuperAdmin(String id);

    int updateProfile(String id, String fullNameTrimmed, String accessLevelIdOrNull, String actorUserId);

    int updateActiveStatus(String id, boolean isActive, String actorUserId);

    Optional<AdminUserDAO.EmailRoleRow> findEmailAndRole(String id);

    String generateTempPassword();

    void resetSupabasePassword(String userId, String newPassword);

    boolean sendResetEmailAndAudit(String userEmail, String newPassword, boolean sendPasswordEmail, String id, String actorUserId);

    Optional<String> findAdminEmail(String id);

    boolean sendPasswordEmail(String userEmail, String password, String context);
}
