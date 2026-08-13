package com.saas.auth.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TenantRepository {

    @Inject
    EntityManager em;

    /** Coluna `type` do tenant (individual/company); vazio se o tenant não existir. */
    public Optional<String> findType(UUID tenantId) {
        var rows = em.createNativeQuery("SELECT type FROM tenants WHERE id = :id")
                .setParameter("id", tenantId)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable((String) rows.get(0));
    }
}
