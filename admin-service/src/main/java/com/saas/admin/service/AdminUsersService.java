package com.saas.admin.service;

import com.saas.admin.client.SupabaseAdminClient;
import com.saas.admin.dao.AdminUserDAO;
import com.saas.admin.dto.AdminUserCreatedDTO;
import com.saas.admin.dto.AdminUserDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gestão de usuários administrativos (system_role SUPER_ADMIN/ADMIN_USER).
 * Persistência isolada em {@link AdminUserDAO}; comunicação com a Admin API
 * do Supabase isolada em {@link SupabaseAdminClient}.
 */
@ApplicationScoped
public class AdminUsersService {

    @Inject
    AdminUserDAO dao;

    @Inject
    SupabaseAdminClient supabaseAdminClient;

    @Inject
    AdminAuditService auditService;

    @Inject
    EmailService emailService;

    public List<AdminUserDTO> list(String search, String status, String accessLevelId) {
        return dao.findAll(search, status, accessLevelId);
    }

    public boolean isAccessLevelActive(String accessLevelId) {
        return dao.countActiveAccessLevel(accessLevelId) > 0;
    }

    public Optional<AdminUserDAO.ExistingUserRow> findExistingByEmail(String normalizedEmail) {
        return dao.findByEmail(normalizedEmail);
    }

    public boolean isSupabaseConfigured() {
        return supabaseAdminClient.isConfigured();
    }

    public String createSupabaseUser(String email, String fullName, String password) {
        return supabaseAdminClient.createUser(email, fullName, password);
    }

    @Transactional
    public Optional<AdminUserCreatedDTO> finalizeCreate(
            String newUserId, String fullNameTrimmed, String accessLevelId, String normalizedEmail,
            String effectivePassword, boolean sendPasswordEmail, String actorUserId) {

        dao.promoteToAdmin(newUserId, fullNameTrimmed, accessLevelId);

        Optional<AdminUserCreatedDTO> found = dao.findCreatedById(newUserId);
        if (found.isEmpty()) return Optional.empty();

        boolean emailSent = false;
        if (sendPasswordEmail) {
            emailSent = emailService.sendAdminUserCreatedEmail(normalizedEmail, effectivePassword);
        }

        auditService.log(actorUserId, "admin_user.create", "user_profiles", newUserId, Map.of("email", normalizedEmail));

        AdminUserCreatedDTO base = found.get();
        return Optional.of(new AdminUserCreatedDTO(
            base.id(), base.email(), base.fullName(), base.systemRole(), base.isActive(),
            base.accessLevelId(), base.accessLevelName(), base.createdAt(), effectivePassword, emailSent));
    }

    public boolean isSuperAdmin(String id) {
        return dao.countSuperAdmin(id) > 0;
    }

    @Transactional
    public int updateProfile(String id, String fullNameTrimmed, String accessLevelIdOrNull, String actorUserId) {
        int updated = accessLevelIdOrNull != null
            ? dao.updateProfileWithAccessLevel(id, fullNameTrimmed, accessLevelIdOrNull)
            : dao.updateProfileClearingAccessLevel(id, fullNameTrimmed);

        if (updated > 0) auditService.log(actorUserId, "admin_user.update", "user_profiles", id, Map.of());
        return updated;
    }

    @Transactional
    public int updateActiveStatus(String id, boolean isActive, String actorUserId) {
        int updated = dao.updateActiveStatus(id, isActive);
        if (updated > 0) auditService.log(actorUserId, "admin_user.status_change", "user_profiles", id, Map.of("isActive", isActive));
        return updated;
    }

    public Optional<AdminUserDAO.EmailRoleRow> findEmailAndRole(String id) {
        return dao.findEmailAndRole(id);
    }

    public String generateTempPassword() {
        String upper   = "ABCDEFGHJKMNPQRSTUVWXYZ";
        String lower   = "abcdefghjkmnpqrstuvwxyz";
        String digits  = "23456789";
        String special = "!@#$%";
        String all     = upper + lower + digits + special;

        SecureRandom rnd = new SecureRandom();
        List<Character> chars = new ArrayList<>();
        chars.add(upper.charAt(rnd.nextInt(upper.length())));
        chars.add(lower.charAt(rnd.nextInt(lower.length())));
        chars.add(digits.charAt(rnd.nextInt(digits.length())));
        chars.add(special.charAt(rnd.nextInt(special.length())));
        for (int i = 0; i < 8; i++) chars.add(all.charAt(rnd.nextInt(all.length())));
        Collections.shuffle(chars, rnd);

        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    public void resetSupabasePassword(String userId, String newPassword) {
        supabaseAdminClient.updateUserPassword(userId, newPassword);
    }

    @Transactional
    public boolean sendResetEmailAndAudit(String userEmail, String newPassword, boolean sendPasswordEmail, String id, String actorUserId) {
        boolean emailSent = false;
        if (sendPasswordEmail) {
            emailSent = emailService.sendAdminUserPasswordResetEmail(userEmail, newPassword);
        }
        auditService.log(actorUserId, "admin_user.reset_password", "user_profiles", id, Map.of());
        return emailSent;
    }

    public Optional<String> findAdminEmail(String id) {
        return dao.findAdminEmail(id);
    }

    public boolean sendPasswordEmail(String userEmail, String password, String context) {
        return "reset".equals(context)
            ? emailService.sendAdminUserPasswordResetEmail(userEmail, password)
            : emailService.sendAdminUserCreatedEmail(userEmail, password);
    }
}
