package com.saas.auth.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserTenantRepository {

    @Inject
    EntityManager em;

    public record UserTenantResult(UUID tenantId, String role) {}

    @SuppressWarnings("unchecked")
    public Optional<UserTenantResult> findByUserAndTenant(UUID userId, UUID tenantId) {
        var result = (java.util.List<Object[]>) em.createNativeQuery(
                "SELECT ut.tenant_id::text, ut.role FROM user_tenants ut " +
                "WHERE ut.user_id = :userId AND ut.tenant_id = :tenantId AND ut.is_active = TRUE"
        )
        .setParameter("userId", userId)
        .setParameter("tenantId", tenantId)
        .getResultList();

        if (result.isEmpty()) return Optional.empty();
        Object[] row = result.get(0);
        return Optional.of(new UserTenantResult(UUID.fromString((String) row[0]), (String) row[1]));
    }

    @SuppressWarnings("unchecked")
    public Optional<UserTenantResult> findDefaultTenant(UUID userId) {
        var result = (java.util.List<Object[]>) em.createNativeQuery(
                "SELECT ut.tenant_id::text, ut.role FROM user_tenants ut " +
                "WHERE ut.user_id = :userId AND ut.is_active = TRUE " +
                "ORDER BY ut.created_at ASC LIMIT 1"
        )
        .setParameter("userId", userId)
        .getResultList();

        if (result.isEmpty()) return Optional.empty();
        Object[] row = result.get(0);
        return Optional.of(new UserTenantResult(UUID.fromString((String) row[0]), (String) row[1]));
    }

    // ─── permissions_version — invalidação de PAT/MAT em cache no frontend ──────
    //
    // O ProfileAccessToken e o ModuleAccessToken carregam permissions_version no
    // momento da emissão. Qualquer alteração de nível de acesso, das permissões de
    // um nível ou de assinatura (feita em profile-service/subscription-service)
    // incrementa essa coluna para que o token em cache seja invalidado sem esperar
    // a expiração natural (8h no PAT, 30min no MAT).

    public record PermissionsStatus(int permissionsVersion, boolean active) {}

    @SuppressWarnings("unchecked")
    public Optional<PermissionsStatus> findPermissionsStatus(UUID userId, UUID tenantId) {
        var rows = (List<Object[]>) em.createNativeQuery(
                "SELECT permissions_version, is_active FROM user_tenants " +
                "WHERE user_id = :userId AND tenant_id = :tenantId"
        )
        .setParameter("userId", userId)
        .setParameter("tenantId", tenantId)
        .getResultList();

        if (rows.isEmpty()) return Optional.empty();
        Object[] row = rows.get(0);
        return Optional.of(new PermissionsStatus(
                ((Number) row[0]).intValue(),
                Boolean.TRUE.equals(row[1])
        ));
    }

    /** Versão vigente para emissão de token; 1 (default da coluna) se o vínculo não existir. */
    public int resolvePermissionsVersion(UUID userId, UUID tenantId) {
        return findPermissionsStatus(userId, tenantId).map(PermissionsStatus::permissionsVersion).orElse(1);
    }
}
