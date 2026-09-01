package com.saas.subscription.dao;

import com.saas.subscription.entity.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserProfileDAO implements PanacheRepositoryBase<UserProfile, UUID> {

    public Optional<UserProfile> findByIdOptional(UUID id) {
        return find("id", id).firstResultOptional();
    }
}
