package com.saas.subscription.dao;

import com.saas.subscription.entity.PlatformModule;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlatformModuleDAO implements PanacheRepositoryBase<PlatformModule, UUID> {

    public List<PlatformModule> listActiveOrderedBySortOrder() {
        return find("isActive = true order by sortOrder").list();
    }

    public Optional<PlatformModule> findActiveBySlug(String slug) {
        return find("slug = ?1 and isActive = true", slug).firstResultOptional();
    }
}
