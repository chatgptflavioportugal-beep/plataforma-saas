package com.saas.subscription.dao;

import com.saas.subscription.entity.AdminAccessLevelPermission;
import com.saas.platformadmin.AdminPermissionResolver;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class AdminAccessLevelPermissionDAO implements PanacheRepositoryBase<AdminAccessLevelPermission, UUID>, AdminPermissionResolver {

    @Override
    public boolean hasPermission(UUID accessLevelId, String permissionKey) {
        return count("accessLevelId = ?1 and permissionKey = ?2", accessLevelId, permissionKey) > 0;
    }
}
