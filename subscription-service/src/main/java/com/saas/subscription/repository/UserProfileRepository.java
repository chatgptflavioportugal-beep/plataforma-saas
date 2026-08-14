package com.saas.subscription.repository;

import com.saas.subscription.entity.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserProfileRepository implements PanacheRepositoryBase<UserProfile, UUID> {

    public Optional<UserProfile> findByIdOptional(UUID id) {
        return find("id", id).firstResultOptional();
    }
}
