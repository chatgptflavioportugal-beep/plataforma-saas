package com.saas.catalog.service;

import com.saas.catalog.dto.ServiceRouteResolutionDTO;
import com.saas.catalog.repository.ServiceCatalogRepository;
import com.saas.catalog.repository.ServiceCatalogRepository.CatalogServiceRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogServiceTest {

    private ServiceCatalogRepository serviceCatalogRepository;
    private CatalogService service;

    @BeforeEach
    void setUp() {
        serviceCatalogRepository = mock(ServiceCatalogRepository.class);

        service = new CatalogService();
        service.serviceCatalogRepository = serviceCatalogRepository;
    }

    @Test
    void resolveRoute_returnsNotFoundWhenRouteKeyDoesNotMatchAnyActiveService() {
        when(serviceCatalogRepository.findActiveByRouteKey("inexistente")).thenReturn(Optional.empty());

        ServiceRouteResolutionDTO result = service.resolveRoute("inexistente");

        assertEquals("NOT_FOUND", result.accessStatus());
        assertEquals("inexistente", result.routeKey());
        assertNull(result.serviceId());
        assertNull(result.permissionKey());
    }

    @Test
    void resolveRoute_buildsPermissionKeyWithGroupWhenServiceBelongsToAGroup() {
        when(serviceCatalogRepository.findActiveByRouteKey("relatorios")).thenReturn(Optional.of(
                new CatalogServiceRow(
                        "service-id", "Relatórios", "relatorios",
                        "module-id", "Financeiro", "financeiro",
                        "relatorios-mensais", "relatorios-grupo")));

        ServiceRouteResolutionDTO result = service.resolveRoute("relatorios");

        assertEquals("FOUND", result.accessStatus());
        assertEquals("financeiro.relatorios-grupo.relatorios-mensais", result.permissionKey());
        assertEquals("financeiro", result.moduleSlug());
    }

    @Test
    void resolveRoute_buildsPermissionKeyWithoutGroupWhenServiceHasNoGroup() {
        when(serviceCatalogRepository.findActiveByRouteKey("dashboard")).thenReturn(Optional.of(
                new CatalogServiceRow(
                        "service-id", "Dashboard", "dashboard",
                        "module-id", "Financeiro", "financeiro",
                        "dashboard", null)));

        ServiceRouteResolutionDTO result = service.resolveRoute("dashboard");

        assertEquals("FOUND", result.accessStatus());
        assertEquals("financeiro.dashboard", result.permissionKey());
    }
}
