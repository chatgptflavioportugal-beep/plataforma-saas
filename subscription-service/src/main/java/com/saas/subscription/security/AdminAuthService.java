package com.saas.subscription.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Checagem de autorização administrativa (SUPER_ADMIN / ADMIN_USER + permissão
 * granular), compartilhada pelos controllers admin deste serviço. Mesma lógica do
 * requireAdminPermission de AdminResource no backend-quarkus.
 */
@ApplicationScoped
public class AdminAuthService {

    @Inject
    EntityManager em;

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
        try {
            Object[] row = (Object[]) em.createNativeQuery(
                "SELECT system_role, is_active, admin_access_level_id::text " +
                "FROM user_profiles WHERE id::text = :id"
            ).setParameter("id", userId).getSingleResult();

            String role = (String) row[0];
            boolean isActive = Boolean.TRUE.equals(row[1]);

            if ("SUPER_ADMIN".equals(role)) return;

            if (!"ADMIN_USER".equals(role))
                throw new ForbiddenException("Acesso restrito à área administrativa");

            if (!isActive)
                throw new ForbiddenException("Usuário administrativo inativo");

            if (permissionKey == null) return;

            String accessLevelId = (String) row[2];
            if (accessLevelId == null)
                throw new ForbiddenException("Você não possui permissão para executar esta ação");

            long has = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM admin_access_level_permissions " +
                "WHERE access_level_id::text = :lvl AND permission_key = :key"
            ).setParameter("lvl", accessLevelId).setParameter("key", permissionKey)
             .getSingleResult()).longValue();

            if (has == 0)
                throw new ForbiddenException("Você não possui permissão para executar esta ação");

        } catch (jakarta.persistence.NoResultException e) {
            throw new ForbiddenException("Perfil de usuário não encontrado");
        }
    }
}
