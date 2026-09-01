package com.saas.auth.negocio;

import com.saas.auth.dao.AccessLevelPermissionDAO;
import com.saas.auth.dao.TenantDAO;
import com.saas.auth.dao.UserTenantDAO;
import com.saas.auth.dto.ProfileTokenResponse;
import com.saas.auth.negocio.impl.ProfileTokenNegocio;
import com.saas.auth.security.TokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orquestra a emissão do ProfileAccessToken (PAT): resolve o tipo de perfil
 * do tenant, carrega as permissões administrativas do usuário (owner/admin
 * têm todas; membros, as do seu nível de acesso) e assina o token.
 */
@ApplicationScoped
public class ProfileTokenNegocioImpl implements ProfileTokenNegocio {

    private static final List<String> OWNER_PERMISSIONS = List.of(
            "profile.dashboard.view",
            "profile.members.view",
            "profile.members.invite",
            "profile.members.remove",
            "profile.members.change_access_level",
            "profile.access_levels.view",
            "profile.access_levels.create",
            "profile.access_levels.edit",
            "profile.access_levels.inactivate",
            "profile.access_levels.delete",
            "profile.plans.view",
            "profile.plans.subscribe",
            "profile.subscriptions.view",
            "profile.subscriptions.cancel",
            "profile.subscriptions.reactivate",
            "profile.company_settings.view",
            "profile.company_settings.edit",
            "profile.invites.view",
            "profile.invites.cancel",
            "profile.invites.resend",
            "profile.billing.view",
            "profile.billing.payment_methods.manage",
            "profile.billing.payment_history.view"
    );

    @Inject
    TenantDAO tenantDAO;

    @Inject
    UserTenantDAO userTenantDAO;

    @Inject
    AccessLevelPermissionDAO accessLevelPermissionDAO;

    @Inject
    TokenService tokenService;

    @Override
    public ProfileTokenResponse issueAccessToken(UUID userId, UUID tenantId, String role) {
        String profileType = resolveProfileType(tenantId);
        int permissionsVersion = userTenantDAO.resolvePermissionsVersion(userId, tenantId);

        String accessLevelId = null;
        List<String> permissions;

        if (List.of("owner", "admin").contains(role)) {
            permissions = new ArrayList<>(OWNER_PERMISSIONS);
        } else {
            permissions = new ArrayList<>();
            for (var row : accessLevelPermissionDAO.findAdminPermissions(userId, tenantId)) {
                if (accessLevelId == null && row.accessLevelId() != null) {
                    accessLevelId = row.accessLevelId();
                }
                if (row.permissionKey() != null) {
                    permissions.add("profile." + row.permissionKey());
                }
            }
            // Todos os membros podem acessar o dashboard
            if (!permissions.contains("profile.dashboard.view")) {
                permissions.add("profile.dashboard.view");
            }
        }

        var issued = tokenService.generateProfileToken(
                userId,
                tenantId,
                profileType,
                toProfileRole(role),
                accessLevelId,
                permissions,
                permissionsVersion
        );

        return new ProfileTokenResponse(issued.token(), issued.expiresAt().toString(), permissions);
    }

    @Override
    public int permissionsVersion(UUID userId, UUID tenantId) {
        return userTenantDAO.resolvePermissionsVersion(userId, tenantId);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /** Tipo do perfil: INDIVIDUAL ou COMPANY (derivado da coluna type do tenant); COMPANY se o tenant não for encontrado. */
    private String resolveProfileType(UUID tenantId) {
        return tenantDAO.findType(tenantId)
                .map(type -> "individual".equals(type) ? "INDIVIDUAL" : "COMPANY")
                .orElse("COMPANY");
    }

    private String toProfileRole(String role) {
        return switch (role) {
            case "owner"      -> "OWNER";
            case "admin"      -> "OWNER";
            case "member"     -> "MEMBER";
            case "individual" -> "INDIVIDUAL";
            default           -> "MEMBER";
        };
    }
}
