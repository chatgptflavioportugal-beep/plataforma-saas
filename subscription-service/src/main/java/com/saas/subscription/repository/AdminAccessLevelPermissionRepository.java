package com.saas.subscription.repository;

import com.saas.subscription.entity.AdminAccessLevelPermission;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class AdminAccessLevelPermissionRepository implements PanacheRepositoryBase<AdminAccessLevelPermission, UUID> {

    public boolean hasPermission(UUID accessLevelId, String permissionKey) {
        return count("accessLevelId = ?1 and permissionKey = ?2", accessLevelId, permissionKey) > 0;
    }
}
