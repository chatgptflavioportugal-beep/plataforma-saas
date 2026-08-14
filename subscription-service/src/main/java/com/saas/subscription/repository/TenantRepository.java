package com.saas.subscription.repository;

import com.saas.subscription.entity.Tenant;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;

import java.util.UUID;

@ApplicationScoped
public class TenantRepository implements PanacheRepositoryBase<Tenant, UUID> {

    public String findType(UUID tenantId) {
        Tenant tenant = findById(tenantId);
        if (tenant == null) throw new NoResultException("Tenant não encontrado: " + tenantId);
        return tenant.type;
    }
}
