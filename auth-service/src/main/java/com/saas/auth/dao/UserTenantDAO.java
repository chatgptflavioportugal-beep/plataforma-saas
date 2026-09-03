package com.saas.auth.dao;

import com.saas.auth.to.PermissionsStatusTO;
import com.saas.auth.to.UserTenantTO;
import com.saas.platformdatabase.query.DatabaseQuery;
import com.saas.platformtenant.TenantMembership;
import com.saas.platformtenant.TenantMembershipResolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserTenantDAO implements TenantMembershipResolver {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    @Override
    public Optional<TenantMembership> findByUserAndTenant(UUID userId, UUID tenantId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT ut.tenant_id::text AS tenant_id, ut.role AS role FROM user_tenants ut
                        WHERE ut.user_id = :userId AND ut.tenant_id = :tenantId AND ut.is_active = TRUE
                        """, UserTenantTO.class)
                .setParameter("userId", userId)
                .setParameter("tenantId", tenantId)
                .getOptionalResult()
                .map(to -> new TenantMembership(to.tenantId(), to.role()));
    }

    @Override
    public Optional<TenantMembership> findDefaultTenant(UUID userId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT ut.tenant_id::text AS tenant_id, ut.role AS role FROM user_tenants ut
                        WHERE ut.user_id = :userId AND ut.is_active = TRUE
                        ORDER BY ut.created_at ASC LIMIT 1
                        """, UserTenantTO.class)
                .setParameter("userId", userId)
                .getOptionalResult()
                .map(to -> new TenantMembership(to.tenantId(), to.role()));
    }

    // ─── permissions_version — invalidação de PAT/MAT em cache no frontend ──────
    //
    // O ProfileAccessToken e o ModuleAccessToken carregam permissions_version no
    // momento da emissão. Qualquer alteração de nível de acesso, das permissões de
    // um nível ou de assinatura (feita em profile-service/subscription-service)
    // incrementa essa coluna para que o token em cache seja invalidado sem esperar
    // a expiração natural (8h no PAT, 30min no MAT).

    public Optional<PermissionsStatusTO> findPermissionsStatus(UUID userId, UUID tenantId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT permissions_version, is_active FROM user_tenants
                        WHERE user_id = :userId AND tenant_id = :tenantId
                        """, PermissionsStatusTO.class)
                .setParameter("userId", userId)
                .setParameter("tenantId", tenantId)
                .getOptionalResult();
    }

    /** Versão vigente para emissão de token; 1 (default da coluna) se o vínculo não existir. */
    public int resolvePermissionsVersion(UUID userId, UUID tenantId) {
        return findPermissionsStatus(userId, tenantId).map(PermissionsStatusTO::permissionsVersion).orElse(1);
    }
}
