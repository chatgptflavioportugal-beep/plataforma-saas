package com.saas.catalog.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ServiceCatalogDAO {

    @Inject
    EntityManager em;

    public record CatalogServiceRow(
            String serviceId,
            String serviceName,
            String routeKey,
            String moduleId,
            String moduleName,
            String moduleSlug,
            String serviceSlug,
            String groupSlug
    ) {}

    @SuppressWarnings("unchecked")
    public Optional<CatalogServiceRow> findActiveByRouteKey(String routeKey) {
        List<Object[]> rows = em.createNativeQuery("""
            SELECT
              s.id::text,
              s.name,
              s.route_key,
              m.id::text,
              m.name,
              m.slug,
              s.slug,
              g.slug
            FROM platform_module_services s
            JOIN platform_modules m ON m.id = s.module_id
            LEFT JOIN platform_module_service_groups g
              ON g.id = s.service_group_id AND g.status = 'ACTIVE'
            WHERE s.route_key = :routeKey
              AND s.is_active = TRUE
              AND m.is_active = TRUE
        """).setParameter("routeKey", routeKey).getResultList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = rows.get(0);
        return Optional.of(new CatalogServiceRow(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                (String) row[6],
                (String) row[7]
        ));
    }
}
