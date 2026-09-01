package com.saas.profile.negocio;

import com.saas.profile.dao.AccessLevelDAO;
import com.saas.profile.dao.UserProfileDAO;
import com.saas.profile.negocio.impl.AdminAccessNegocio;
import com.saas.profile.security.TenantContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.List;
import java.util.UUID;

/**
 * Checagens de autorização hoje duplicadas entre Resources: permissão administrativa de
 * tenant (owner/admin sempre têm; membro depende do nível de acesso) e o flag de
 * administrador de plataforma (system_role SUPER_ADMIN/ADMIN_USER), usado para impedir
 * que um usuário administrativo vire membro de empresa cliente.
 */
@ApplicationScoped
public class AdminAccessNegocioImpl implements AdminAccessNegocio {

    @Inject
    AccessLevelDAO accessLevelDAO;

    @Inject
    UserProfileDAO userProfileDAO;

    public boolean isPlatformAdmin(UUID userId) {
        return userProfileDAO.findSystemRole(userId)
                .map(role -> "SUPER_ADMIN".equals(role) || "ADMIN_USER".equals(role))
                .orElse(false);
    }

    public boolean hasAdminPerm(TenantContext tc, UUID tenantId, String permKey) {
        if (List.of("owner", "admin").contains(tc.getUserRole())) return true;
        return accessLevelDAO.hasAdminPermission(tc.getUserId(), tenantId, permKey);
    }

    public void requireAdminPerm(TenantContext tc, UUID tenantId, String permKey) {
        if (!hasAdminPerm(tc, tenantId, permKey)) {
            throw new ForbiddenException("Permissão necessária: " + permKey);
        }
    }
}
