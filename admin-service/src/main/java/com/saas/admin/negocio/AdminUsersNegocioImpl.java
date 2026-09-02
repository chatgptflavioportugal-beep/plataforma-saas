package com.saas.admin.negocio;

import com.saas.admin.dao.AdminUserDAO;
import com.saas.admin.dto.AdminUserCreatedDTO;
import com.saas.admin.dto.AdminUserDTO;
import com.saas.admin.negocio.impl.AdminAuditNegocio;
import com.saas.admin.negocio.impl.AdminUsersNegocio;
import com.saas.admin.repository.EmailRepository;
import com.saas.admin.repository.SupabaseAdminRepository;
import com.saas.admin.to.EmailRoleTO;
import com.saas.admin.to.ExistingUserTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AdminUsersNegocioImpl implements AdminUsersNegocio {

    @Inject
    AdminUserDAO dao;

    @Inject
    SupabaseAdminRepository supabaseAdminRepository;

    @Inject
    AdminAuditNegocio auditNegocio;

    @Inject
    EmailRepository emailRepository;

    @Override
    public List<AdminUserDTO> list(String search, String status, String accessLevelId) {
        return dao.findAll(search, status, accessLevelId);
    }

    @Override
    public boolean isAccessLevelActive(String accessLevelId) {
        return dao.countActiveAccessLevel(accessLevelId) > 0;
    }

    @Override
    public Optional<ExistingUserTO> findExistingByEmail(String normalizedEmail) {
        return dao.findByEmail(normalizedEmail);
    }

    @Override
    public boolean isSupabaseConfigured() {
        return supabaseAdminRepository.isConfigured();
    }

    @Override
    public String createSupabaseUser(String email, String fullName, String password) {
        return supabaseAdminRepository.createUser(email, fullName, password);
    }

    @Override
    @Transactional
    public Optional<AdminUserCreatedDTO> finalizeCreate(
            String newUserId, String fullNameTrimmed, String accessLevelId, String normalizedEmail,
            String effectivePassword, boolean sendPasswordEmail, String actorUserId) {

        dao.promoteToAdmin(newUserId, fullNameTrimmed, accessLevelId);

        Optional<AdminUserCreatedDTO> found = dao.findCreatedById(newUserId);
        if (found.isEmpty()) return Optional.empty();

        boolean emailSent = false;
        if (sendPasswordEmail) {
            emailSent = emailRepository.sendAdminUserCreatedEmail(normalizedEmail, effectivePassword);
        }

        auditNegocio.log(actorUserId, "admin_user.create", "user_profiles", newUserId, Map.of("email", normalizedEmail));

        AdminUserCreatedDTO base = found.get();
        return Optional.of(new AdminUserCreatedDTO(
            base.id(), base.email(), base.fullName(), base.systemRole(), base.isActive(),
            base.accessLevelId(), base.accessLevelName(), base.createdAt(), effectivePassword, emailSent));
    }

    @Override
    public boolean isSuperAdmin(String id) {
        return dao.countSuperAdmin(id) > 0;
    }

    @Override
    @Transactional
    public int updateProfile(String id, String fullNameTrimmed, String accessLevelIdOrNull, String actorUserId) {
        int updated = accessLevelIdOrNull != null
            ? dao.updateProfileWithAccessLevel(id, fullNameTrimmed, accessLevelIdOrNull)
            : dao.updateProfileClearingAccessLevel(id, fullNameTrimmed);

        if (updated > 0) auditNegocio.log(actorUserId, "admin_user.update", "user_profiles", id, Map.of());
        return updated;
    }

    @Override
    @Transactional
    public int updateActiveStatus(String id, boolean isActive, String actorUserId) {
        int updated = dao.updateActiveStatus(id, isActive);
        if (updated > 0) auditNegocio.log(actorUserId, "admin_user.status_change", "user_profiles", id, Map.of("isActive", isActive));
        return updated;
    }

    @Override
    public Optional<EmailRoleTO> findEmailAndRole(String id) {
        return dao.findEmailAndRole(id);
    }

    @Override
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

    @Override
    public void resetSupabasePassword(String userId, String newPassword) {
        supabaseAdminRepository.updateUserPassword(userId, newPassword);
    }

    @Override
    @Transactional
    public boolean sendResetEmailAndAudit(String userEmail, String newPassword, boolean sendPasswordEmail, String id, String actorUserId) {
        boolean emailSent = false;
        if (sendPasswordEmail) {
            emailSent = emailRepository.sendAdminUserPasswordResetEmail(userEmail, newPassword);
        }
        auditNegocio.log(actorUserId, "admin_user.reset_password", "user_profiles", id, Map.of());
        return emailSent;
    }

    @Override
    public Optional<String> findAdminEmail(String id) {
        return dao.findAdminEmail(id);
    }

    @Override
    public boolean sendPasswordEmail(String userEmail, String password, String context) {
        return "reset".equals(context)
            ? emailRepository.sendAdminUserPasswordResetEmail(userEmail, password)
            : emailRepository.sendAdminUserCreatedEmail(userEmail, password);
    }
}
