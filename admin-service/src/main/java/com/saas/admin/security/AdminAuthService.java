package com.saas.admin.security;

import com.saas.admin.to.AdminAuthProfileTO;
import com.saas.platformdatabase.query.DatabaseQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Optional;

/**
 * Checagem de autorização administrativa (SUPER_ADMIN / ADMIN_USER + permissão
 * granular). Mesma lógica do requireAdminPermission de AdminResource no
 * backend-quarkus (e do AdminAuthService já usado no subscription-service).
 */
@ApplicationScoped
public class AdminAuthService {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

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
        String userId = currentUserId();

        Optional<AdminAuthProfileTO> profile = databaseQuery
                .nativeQuery(em, """
                        SELECT system_role, is_active, admin_access_level_id::text
                        FROM user_profiles WHERE id::text = :id
                        """, AdminAuthProfileTO.class)
                .setParameter("id", userId)
                .getOptionalResult();

        if (profile.isEmpty()) {
            throw new ForbiddenException("Perfil de usuário não encontrado");
        }

        AdminAuthProfileTO row = profile.get();
        String role = row.systemRole();
        boolean isActive = Boolean.TRUE.equals(row.isActive());

        if ("SUPER_ADMIN".equals(role)) return;

        if (!"ADMIN_USER".equals(role))
            throw new ForbiddenException("Acesso restrito à área administrativa");

        if (!isActive)
            throw new ForbiddenException("Usuário administrativo inativo");

        if (permissionKey == null) return;

        String accessLevelId = row.adminAccessLevelId();
        if (accessLevelId == null)
            throw new ForbiddenException("Você não possui permissão para executar esta ação");

        long has = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM admin_access_level_permissions " +
            "WHERE access_level_id::text = :lvl AND permission_key = :key"
        ).setParameter("lvl", accessLevelId).setParameter("key", permissionKey)
         .getSingleResult()).longValue();

        if (has == 0)
            throw new ForbiddenException("Você não possui permissão para executar esta ação");
    }
}
