package com.saas.subscription.security;

import com.saas.subscription.entity.UserProfile;
import com.saas.subscription.repository.AdminAccessLevelPermissionRepository;
import com.saas.subscription.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

/**
 * Checagem de autorização administrativa (SUPER_ADMIN / ADMIN_USER + permissão
 * granular), lida diretamente do banco. Mesma lógica de
 * com.saas.admin.security.AdminAuthService no admin-service — cada serviço
 * consulta user_profiles/admin_access_level_permissions localmente em vez de
 * depender de uma chamada HTTP para outro microsserviço.
 */
@ApplicationScoped
public class AdminAuthService {

    @Inject
    UserProfileRepository userProfileRepository;

    @Inject
    AdminAccessLevelPermissionRepository adminAccessLevelPermissionRepository;

    @Inject
    JsonWebToken jwt;

    public String currentUserId() {
        String userId = jwt.getSubject();
        if (userId == null) throw new ForbiddenException("Não autenticado");
        return userId;
    }

    /**
     * Exige que o usuário seja SUPER_ADMIN ou ADMIN_USER ativo com a permissão especificada.
     * Passar null em permissionKey verifica apenas que é um admin válido (para listagens gerais).
     */
    public void requireAdminPermission(String permissionKey) {
        UUID userId = UUID.fromString(currentUserId());
        UserProfile profile = userProfileRepository.findByIdOptional(userId)
            .orElseThrow(() -> new ForbiddenException("Perfil de usuário não encontrado"));

        if ("SUPER_ADMIN".equals(profile.systemRole)) return;

        if (!"ADMIN_USER".equals(profile.systemRole))
            throw new ForbiddenException("Acesso restrito à área administrativa");

        if (!profile.isActive)
            throw new ForbiddenException("Usuário administrativo inativo");

        if (permissionKey == null) return;

        if (profile.adminAccessLevelId == null)
            throw new ForbiddenException("Você não possui permissão para executar esta ação");

        boolean hasPermission = adminAccessLevelPermissionRepository
            .hasPermission(profile.adminAccessLevelId, permissionKey);

        if (!hasPermission)
            throw new ForbiddenException("Você não possui permissão para executar esta ação");
    }
}
