package com.saas.auth.negocio;

import com.saas.auth.dao.ModuleServiceDAO;
import com.saas.auth.dao.UserTenantDAO;
import com.saas.auth.repository.ModuleAccessResolution;
import com.saas.auth.repository.SubscriptionServiceRepository;
import com.saas.auth.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModuleTokenNegocioImplTest {

    private static final String MODULE_SLUG = "pdf";

    private SubscriptionServiceRepository subscriptionServiceRepository;
    private ModuleServiceDAO moduleServiceDAO;
    private UserTenantDAO userTenantDAO;
    private TokenService tokenService;
    private ModuleTokenNegocioImpl negocio;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID moduleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        subscriptionServiceRepository = mock(SubscriptionServiceRepository.class);
        moduleServiceDAO = mock(ModuleServiceDAO.class);
        userTenantDAO = mock(UserTenantDAO.class);
        tokenService = mock(TokenService.class);

        negocio = new ModuleTokenNegocioImpl();
        negocio.subscriptionServiceRepository = subscriptionServiceRepository;
        negocio.moduleServiceDAO = moduleServiceDAO;
        negocio.userTenantDAO = userTenantDAO;
        negocio.tokenService = tokenService;

        when(userTenantDAO.resolvePermissionsVersion(userId, tenantId)).thenReturn(2);
        when(tokenService.generateModuleToken(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new TokenService.IssuedToken("module-token", Instant.parse("2026-01-01T00:30:00Z")));
    }

    @Test
    void grantedResolutionIssuesTokenWithOwnerPermissions() {
        when(subscriptionServiceRepository.resolveModuleAccess(any(), eq(tenantId.toString()), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("GRANTED", moduleId.toString(), "PDF", "Pro",
                        "SUBSCRIPTION", Map.of("maxDocs", 100), null));
        when(moduleServiceDAO.findActiveServiceSlugsByModule(moduleId))
                .thenReturn(List.of("generate", "sign"));

        var result = negocio.issue(userId, tenantId, "owner", MODULE_SLUG, "Bearer jwt");

        assertInstanceOf(ModuleTokenResult.Issued.class, result);
        var response = ((ModuleTokenResult.Issued) result).response();
        assertEquals("module-token", response.moduleAccessToken());
        assertEquals("PDF", response.moduleName());
        assertEquals("Pro", response.planName());
        assertEquals(List.of("generate", "sign"), response.permissions());
        assertEquals("2026-01-01T00:30:00Z", response.expiresAt());

        verify(moduleServiceDAO, never()).findServiceSlugsByAccessLevel(any(), any(), any());
    }

    @Test
    void grantedResolutionForMemberUsesAccessLevelPermissions() {
        when(subscriptionServiceRepository.resolveModuleAccess(any(), eq(tenantId.toString()), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("GRANTED", moduleId.toString(), "PDF", null,
                        "TENANT_SUBSCRIPTION", Map.of(), null));
        when(moduleServiceDAO.findServiceSlugsByAccessLevel(userId, tenantId, moduleId))
                .thenReturn(List.of("generate"));

        var result = negocio.issue(userId, tenantId, "member", MODULE_SLUG, "Bearer jwt");

        var response = ((ModuleTokenResult.Issued) result).response();
        assertEquals(List.of("generate"), response.permissions());
        assertEquals("", response.planName());

        verify(moduleServiceDAO, never()).findActiveServiceSlugsByModule(any());
    }

    @Test
    void moduleNotFoundResolutionIsMappedWithoutIssuingToken() {
        when(subscriptionServiceRepository.resolveModuleAccess(any(), any(), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("MODULE_NOT_FOUND", null, null, null, null, null, null));

        var result = negocio.issue(userId, tenantId, "owner", MODULE_SLUG, "Bearer jwt");

        assertEquals(new ModuleTokenResult.NotFound(MODULE_SLUG), result);
        verifyNoInteractions(tokenService);
    }

    @Test
    void moduleExpiredResolutionIsMapped() {
        when(subscriptionServiceRepository.resolveModuleAccess(any(), any(), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("MODULE_EXPIRED", moduleId.toString(), "PDF", null, null, null, null));

        var result = negocio.issue(userId, tenantId, "owner", MODULE_SLUG, "Bearer jwt");

        assertEquals(new ModuleTokenResult.Expired(MODULE_SLUG), result);
    }

    @Test
    void freePlanNotActivatedResolutionIsMapped() {
        when(subscriptionServiceRepository.resolveModuleAccess(any(), any(), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("FREE_PLAN_NOT_ACTIVATED", moduleId.toString(), "PDF",
                        null, null, null, "plan-version-id"));

        var result = negocio.issue(userId, tenantId, "owner", MODULE_SLUG, "Bearer jwt");

        assertEquals(new ModuleTokenResult.FreePlanNotActivated(MODULE_SLUG, moduleId.toString(), "plan-version-id"), result);
    }

    @Test
    void noAccessResolutionIsMapped() {
        when(subscriptionServiceRepository.resolveModuleAccess(any(), any(), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("NO_ACCESS", moduleId.toString(), "PDF", null, null, null, null));

        var result = negocio.issue(userId, tenantId, "owner", MODULE_SLUG, "Bearer jwt");

        assertEquals(new ModuleTokenResult.NoAccess(MODULE_SLUG), result);
    }
}
