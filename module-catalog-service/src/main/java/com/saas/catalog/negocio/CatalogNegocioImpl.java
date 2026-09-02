package com.saas.catalog.negocio;

import com.saas.catalog.dao.ServiceCatalogDAO;
import com.saas.catalog.dto.ServiceRouteResolutionDTO;
import com.saas.catalog.to.CatalogServiceTO;
import com.saas.catalog.negocio.impl.CatalogNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CatalogNegocioImpl implements CatalogNegocio {

    @Inject
    ServiceCatalogDAO serviceCatalogDAO;

    /**
     * Resolve um serviço pelo routeKey — catálogo puro, sem checagem de assinatura
     * ou permissão (isso é responsabilidade do subscription-service/profile-service;
     * o frontend resolve o accessStatus separadamente via subscription-service).
     */
    @Override
    public ServiceRouteResolutionDTO resolveRoute(String routeKey) {
        return serviceCatalogDAO.findActiveByRouteKey(routeKey)
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
    private String buildPermissionKey(CatalogServiceTO row) {
        return (row.groupSlug() != null)
                ? row.moduleSlug() + "." + row.groupSlug() + "." + row.serviceSlug()
                : row.moduleSlug() + "." + row.serviceSlug();
    }
}
