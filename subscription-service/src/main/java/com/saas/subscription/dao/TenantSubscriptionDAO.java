package com.saas.subscription.dao;

import com.saas.subscription.entity.TenantSubscription;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class TenantSubscriptionDAO implements PanacheRepositoryBase<TenantSubscription, UUID> {

    @Inject
    EntityManager em;

    public record SubscriptionResult(
            UUID id,
            String status,
            String planCode,
            Set<String> moduleSlugSet
    ) {}

    public Optional<SubscriptionResult> findActiveByTenant(UUID tenantId) {
        var accountRows = em.createQuery("""
                select ts.id, ts.status, p.code from TenantSubscription ts
                join Plan p on p.id = ts.planId
                where ts.tenantId = :tenantId and ts.status != 'cancelled'
                order by ts.createdAt desc
            """, Object[].class)
                .setParameter("tenantId", tenantId)
                .setMaxResults(1)
                .getResultList();

        if (accountRows.isEmpty()) return Optional.empty();
        Object[] row = accountRows.get(0);

        var slugRows = em.createQuery("""
                select distinct pm.slug from PlatformModule pm
                where pm.isActive = true
                  and exists (
                    select 1 from ProfileModuleSubscription pms
                    where pms.tenantId = :tenantId
                      and pms.moduleId = pm.id
                      and pms.status = 'ACTIVE'
                      and (pms.expiresAt is null or pms.expiresAt > current_timestamp)
                  )
            """, String.class)
                .setParameter("tenantId", tenantId)
                .getResultList();

        Set<String> moduleSlugSet = new HashSet<>(slugRows);

        return Optional.of(new SubscriptionResult(
                (UUID) row[0],
                (String) row[1],
                (String) row[2],
                moduleSlugSet
        ));
    }
}
