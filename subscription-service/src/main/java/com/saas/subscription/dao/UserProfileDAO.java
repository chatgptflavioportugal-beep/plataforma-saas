package com.saas.subscription.dao;

import com.saas.subscription.entity.UserProfile;
import com.saas.platformadmin.AdminProfile;
import com.saas.platformadmin.AdminProfileResolver;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserProfileDAO implements PanacheRepositoryBase<UserProfile, UUID>, AdminProfileResolver {

    public Optional<UserProfile> findByIdOptional(UUID id) {
        return find("id", id).firstResultOptional();
    }

    @Override
    public Optional<AdminProfile> findProfile(UUID userId) {
        return findByIdOptional(userId)
                .map(p -> new AdminProfile(p.systemRole, p.isActive, p.adminAccessLevelId));
    }
}
