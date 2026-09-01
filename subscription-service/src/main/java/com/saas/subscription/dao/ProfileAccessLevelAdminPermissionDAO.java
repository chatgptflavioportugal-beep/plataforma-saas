package com.saas.subscription.dao;

import com.saas.subscription.entity.ProfileAccessLevelAdminPermission;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.UUID;

@ApplicationScoped
public class ProfileAccessLevelAdminPermissionDAO implements PanacheRepositoryBase<ProfileAccessLevelAdminPermission, UUID> {

    @Inject
    EntityManager em;

    /** O membro (userId) possui a permissão administrativa do perfil informada, no tenant. */
    public boolean memberHasPermission(UUID userId, UUID tenantId, String permissionKey) {
        long count = em.createQuery("""
            select count(ap) from ProfileAccessLevelAdminPermission ap
            where ap.permissionKey = :key and ap.accessLevelId in (
                select ut.accessLevelId from UserTenant ut
                where ut.userId = :userId and ut.tenantId = :tenantId and ut.isActive = true
            )
        """, Long.class)
            .setParameter("userId", userId)
            .setParameter("tenantId", tenantId)
            .setParameter("key", permissionKey)
            .getSingleResult();
        return count > 0;
    }
}
