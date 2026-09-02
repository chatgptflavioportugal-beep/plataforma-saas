package com.saas.catalog.negocio;

import com.saas.catalog.dao.ServiceCatalogDAO;
import com.saas.catalog.dto.ServiceRouteResolutionDTO;
import com.saas.catalog.negocio.CatalogNegocioImpl;
import com.saas.catalog.to.CatalogServiceTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogNegocioImplTest {

    private ServiceCatalogDAO serviceCatalogDAO;
    private CatalogNegocioImpl negocio;

    @BeforeEach
    void setUp() {
        serviceCatalogDAO = mock(ServiceCatalogDAO.class);

        negocio = new CatalogNegocioImpl();
        negocio.serviceCatalogDAO = serviceCatalogDAO;
    }

    @Test
    void resolveRoute_returnsNotFoundWhenRouteKeyDoesNotMatchAnyActiveService() {
        when(serviceCatalogDAO.findActiveByRouteKey("inexistente")).thenReturn(Optional.empty());

        ServiceRouteResolutionDTO result = negocio.resolveRoute("inexistente");

        assertEquals("NOT_FOUND", result.accessStatus());
        assertEquals("inexistente", result.routeKey());
        assertNull(result.serviceId());
        assertNull(result.permissionKey());
    }

    @Test
    void resolveRoute_buildsPermissionKeyWithGroupWhenServiceBelongsToAGroup() {
        when(serviceCatalogDAO.findActiveByRouteKey("relatorios")).thenReturn(Optional.of(
                new CatalogServiceTO(
                        "service-id", "Relatórios", "relatorios",
                        "module-id", "Financeiro", "financeiro",
                        "relatorios-mensais", "relatorios-grupo")));

        ServiceRouteResolutionDTO result = negocio.resolveRoute("relatorios");

        assertEquals("FOUND", result.accessStatus());
        assertEquals("financeiro.relatorios-grupo.relatorios-mensais", result.permissionKey());
        assertEquals("financeiro", result.moduleSlug());
    }

    @Test
    void resolveRoute_buildsPermissionKeyWithoutGroupWhenServiceHasNoGroup() {
        when(serviceCatalogDAO.findActiveByRouteKey("dashboard")).thenReturn(Optional.of(
                new CatalogServiceTO(
                        "service-id", "Dashboard", "dashboard",
                        "module-id", "Financeiro", "financeiro",
                        "dashboard", null)));

        ServiceRouteResolutionDTO result = negocio.resolveRoute("dashboard");

        assertEquals("FOUND", result.accessStatus());
        assertEquals("financeiro.dashboard", result.permissionKey());
    }
}
