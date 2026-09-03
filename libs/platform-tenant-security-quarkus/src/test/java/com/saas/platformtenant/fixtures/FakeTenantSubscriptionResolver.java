package com.saas.platformtenant.fixtures;

import com.saas.platformtenant.TenantSubscriptionInfo;
import com.saas.platformtenant.TenantSubscriptionResolver;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Fixture de teste — só o DEFAULT_TENANT_ID tem assinatura ativa; MEMBER_TENANT_ID cobre o
 *  caminho tolerante (membro sem assinatura). */
@ApplicationScoped
public class FakeTenantSubscriptionResolver implements TenantSubscriptionResolver {

    @Override
    public Optional<TenantSubscriptionInfo> findActiveByTenant(UUID tenantId) {
        if (FakeTenantMembershipResolver.DEFAULT_TENANT_ID.equals(tenantId)) {
            return Optional.of(new TenantSubscriptionInfo(UUID.randomUUID(), "active", "PRO", Set.of("pdf")));
        }
        return Optional.empty();
    }
}
