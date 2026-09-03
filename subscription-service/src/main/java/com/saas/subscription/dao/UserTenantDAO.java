package com.saas.subscription.dao;

import com.saas.subscription.entity.UserTenant;
import com.saas.platformtenant.TenantMembership;
import com.saas.platformtenant.TenantMembershipResolver;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserTenantDAO implements PanacheRepositoryBase<UserTenant, UUID>, TenantMembershipResolver {

    @Override
    public Optional<TenantMembership> findByUserAndTenant(UUID userId, UUID tenantId) {
        return find("userId = ?1 and tenantId = ?2 and isActive = true", userId, tenantId)
            .firstResultOptional()
            .map(ut -> new TenantMembership(ut.tenantId, ut.role));
    }

    @Override
    public Optional<TenantMembership> findDefaultTenant(UUID userId) {
        return find("userId = ?1 and isActive = true order by createdAt asc", userId)
            .firstResultOptional()
            .map(ut -> new TenantMembership(ut.tenantId, ut.role));
    }

    /** Incrementa a versão de todos os membros ativos de um tenant (mudança de assinatura/plano). */
    public void bumpVersionForTenant(UUID tenantId) {
        update("permissionsVersion = permissionsVersion + 1 where tenantId = ?1 and isActive = true", tenantId);
    }

    /** Mesma operação em lote, para múltiplos tenants (usado pelo scheduler de expiração). */
    public void bumpVersionForTenants(java.util.List<UUID> tenantIds) {
        update("permissionsVersion = permissionsVersion + 1 where tenantId in ?1 and isActive = true", tenantIds);
    }
}
