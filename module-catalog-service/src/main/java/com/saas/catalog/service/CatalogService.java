package com.saas.catalog.service;

import com.saas.catalog.dto.ServiceRouteResolutionDTO;
import com.saas.catalog.repository.ServiceCatalogRepository;
import com.saas.catalog.repository.ServiceCatalogRepository.CatalogServiceRow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CatalogService {

    @Inject
    ServiceCatalogRepository serviceCatalogRepository;

    /**
     * Resolve um serviço pelo routeKey — catálogo puro, sem checagem de assinatura
     * ou permissão (isso é responsabilidade do subscription-service/profile-service;
     * o frontend resolve o accessStatus separadamente via subscription-service).
     */
    public ServiceRouteResolutionDTO resolveRoute(String routeKey) {
        return serviceCatalogRepository.findActiveByRouteKey(routeKey)
                .map(row -> ServiceRouteResolutionDTO.found(
                        row.serviceId(),
                        row.serviceName(),
                        row.routeKey(),
                        buildPermissionKey(row),
                        row.moduleId(),
                        row.moduleName(),
                        row.moduleSlug()))
                .orElseGet(() -> ServiceRouteResolutionDTO.notFound(routeKey));
    }

    // Monta permission_key do mesmo modo que o frontend: module.group?.service
    private String buildPermissionKey(CatalogServiceRow row) {
        return (row.groupSlug() != null)
                ? row.moduleSlug() + "." + row.groupSlug() + "." + row.serviceSlug()
                : row.moduleSlug() + "." + row.serviceSlug();
    }
}
