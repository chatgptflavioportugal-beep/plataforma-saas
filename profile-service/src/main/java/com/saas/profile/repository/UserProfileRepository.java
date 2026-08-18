package com.saas.profile.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserProfileRepository {

    @Inject
    EntityManager em;

    public Optional<String> findSystemRole(UUID userId) {
        try {
            String role = (String) em.createNativeQuery(
                    "SELECT system_role FROM user_profiles WHERE id::text = :id"
            ).setParameter("id", userId.toString()).getSingleResult();
            return Optional.ofNullable(role);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<String> findFullName(UUID userId) {
        try {
            String fullName = (String) em.createNativeQuery(
                    "SELECT full_name FROM user_profiles WHERE id = :userId"
            ).setParameter("userId", userId).getSingleResult();
            return Optional.ofNullable(fullName);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
