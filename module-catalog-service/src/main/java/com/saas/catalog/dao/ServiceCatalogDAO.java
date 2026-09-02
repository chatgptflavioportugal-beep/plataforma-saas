package com.saas.catalog.dao;

import com.saas.catalog.to.CatalogServiceTO;
import com.saas.platformdatabase.query.DatabaseQuery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;

@ApplicationScoped
public class ServiceCatalogDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    public Optional<CatalogServiceTO> findActiveByRouteKey(String routeKey) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT
                          s.id::text AS service_id,
                          s.name AS service_name,
                          s.route_key AS route_key,
                          m.id::text AS module_id,
                          m.name AS module_name,
                          m.slug AS module_slug,
                          s.slug AS service_slug,
                          g.slug AS group_slug
                        FROM platform_module_services s
                        JOIN platform_modules m ON m.id = s.module_id
                        LEFT JOIN platform_module_service_groups g
                          ON g.id = s.service_group_id AND g.status = 'ACTIVE'
                        WHERE s.route_key = :routeKey
                          AND s.is_active = TRUE
                          AND m.is_active = TRUE
                        """, CatalogServiceTO.class)
                .setParameter("routeKey", routeKey)
                .getOptionalResult();
    }
}
