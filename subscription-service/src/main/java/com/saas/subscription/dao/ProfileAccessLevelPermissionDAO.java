package com.saas.subscription.dao;

import com.saas.subscription.entity.ProfileAccessLevelPermission;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ProfileAccessLevelPermissionDAO implements PanacheRepositoryBase<ProfileAccessLevelPermission, UUID> {

    @Inject
    EntityManager em;

    /** IDs de serviço liberados para um membro (por access_level_id), no tenant informado. */
    public Set<UUID> findServiceIdsForMember(UUID userId, UUID tenantId) {
        var ids = em.createQuery("""
            select palp.serviceId from ProfileAccessLevelPermission palp
            where palp.accessLevelId in (
                select ut.accessLevelId from UserTenant ut
                where ut.userId = :userId and ut.tenantId = :tenantId and ut.isActive = true
            )
        """, UUID.class)
            .setParameter("userId", userId)
            .setParameter("tenantId", tenantId)
            .getResultList();
        return Set.copyOf(ids);
    }
}
