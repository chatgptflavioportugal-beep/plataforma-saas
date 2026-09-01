package com.saas.catalog.negocio.impl;

import com.saas.catalog.dto.ServiceRouteResolutionDTO;

public interface CatalogNegocio {

    /**
     * Resolve um serviço pelo routeKey — catálogo puro, sem checagem de assinatura
     * ou permissão (isso é responsabilidade do subscription-service/profile-service;
     * o frontend resolve o accessStatus separadamente via subscription-service).
     */
    ServiceRouteResolutionDTO resolveRoute(String routeKey);
}
