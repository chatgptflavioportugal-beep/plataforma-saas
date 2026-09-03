package com.saas.platformtenant.fixtures;

import com.saas.platformtenant.TenantMembership;
import com.saas.platformtenant.TenantMembershipResolver;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

/** Fixture de teste — dados fixos, sem banco. */
@ApplicationScoped
public class FakeTenantMembershipResolver implements TenantMembershipResolver {

    public static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID USER_WITHOUT_DEFAULT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID DEFAULT_TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID MEMBER_TENANT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    public static final UUID FOREIGN_TENANT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Override
    public Optional<TenantMembership> findByUserAndTenant(UUID userId, UUID tenantId) {
        if (USER_ID.equals(userId) && (DEFAULT_TENANT_ID.equals(tenantId) || MEMBER_TENANT_ID.equals(tenantId))) {
            return Optional.of(new TenantMembership(tenantId, "OWNER"));
        }
        return Optional.empty();
    }

    @Override
    public Optional<TenantMembership> findDefaultTenant(UUID userId) {
        if (USER_ID.equals(userId)) {
            return Optional.of(new TenantMembership(DEFAULT_TENANT_ID, "OWNER"));
        }
        return Optional.empty();
    }
}
