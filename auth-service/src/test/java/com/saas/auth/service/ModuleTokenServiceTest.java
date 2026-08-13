package com.saas.auth.service;

import com.saas.auth.client.ModuleAccessResolution;
import com.saas.auth.client.SubscriptionServiceClient;
import com.saas.auth.repository.ModuleServiceRepository;
import com.saas.auth.repository.UserTenantRepository;
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

class ModuleTokenServiceTest {

    private static final String MODULE_SLUG = "pdf";

    private SubscriptionServiceClient subscriptionServiceClient;
    private ModuleServiceRepository moduleServiceRepository;
    private UserTenantRepository userTenantRepository;
    private TokenService tokenService;
    private ModuleTokenService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID moduleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        subscriptionServiceClient = mock(SubscriptionServiceClient.class);
        moduleServiceRepository = mock(ModuleServiceRepository.class);
        userTenantRepository = mock(UserTenantRepository.class);
        tokenService = mock(TokenService.class);

        service = new ModuleTokenService();
        service.subscriptionServiceClient = subscriptionServiceClient;
        service.moduleServiceRepository = moduleServiceRepository;
        service.userTenantRepository = userTenantRepository;
        service.tokenService = tokenService;

        when(userTenantRepository.resolvePermissionsVersion(userId, tenantId)).thenReturn(2);
        when(tokenService.generateModuleToken(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new TokenService.IssuedToken("module-token", Instant.parse("2026-01-01T00:30:00Z")));
    }

    @Test
    void grantedResolutionIssuesTokenWithOwnerPermissions() {
        when(subscriptionServiceClient.resolveModuleAccess(any(), eq(tenantId.toString()), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("GRANTED", moduleId.toString(), "PDF", "Pro",
                        "SUBSCRIPTION", Map.of("maxDocs", 100), null));
        when(moduleServiceRepository.findActiveServiceSlugsByModule(moduleId))
                .thenReturn(List.of("generate", "sign"));

        var result = service.issue(userId, tenantId, "owner", MODULE_SLUG, "Bearer jwt");

        assertInstanceOf(ModuleTokenResult.Issued.class, result);
        var response = ((ModuleTokenResult.Issued) result).response();
        assertEquals("module-token", response.moduleAccessToken());
        assertEquals("PDF", response.moduleName());
        assertEquals("Pro", response.planName());
        assertEquals(List.of("generate", "sign"), response.permissions());
        assertEquals("2026-01-01T00:30:00Z", response.expiresAt());

        verify(moduleServiceRepository, never()).findServiceSlugsByAccessLevel(any(), any(), any());
    }

    @Test
    void grantedResolutionForMemberUsesAccessLevelPermissions() {
        when(subscriptionServiceClient.resolveModuleAccess(any(), eq(tenantId.toString()), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("GRANTED", moduleId.toString(), "PDF", null,
                        "TENANT_SUBSCRIPTION", Map.of(), null));
        when(moduleServiceRepository.findServiceSlugsByAccessLevel(userId, tenantId, moduleId))
                .thenReturn(List.of("generate"));

        var result = service.issue(userId, tenantId, "member", MODULE_SLUG, "Bearer jwt");

        var response = ((ModuleTokenResult.Issued) result).response();
        assertEquals(List.of("generate"), response.permissions());
        assertEquals("", response.planName());

        verify(moduleServiceRepository, never()).findActiveServiceSlugsByModule(any());
    }

    @Test
    void moduleNotFoundResolutionIsMappedWithoutIssuingToken() {
        when(subscriptionServiceClient.resolveModuleAccess(any(), any(), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("MODULE_NOT_FOUND", null, null, null, null, null, null));

        var result = service.issue(userId, tenantId, "owner", MODULE_SLUG, "Bearer jwt");

        assertEquals(new ModuleTokenResult.NotFound(MODULE_SLUG), result);
        verifyNoInteractions(tokenService);
    }

    @Test
    void moduleExpiredResolutionIsMapped() {
        when(subscriptionServiceClient.resolveModuleAccess(any(), any(), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("MODULE_EXPIRED", moduleId.toString(), "PDF", null, null, null, null));

        var result = service.issue(userId, tenantId, "owner", MODULE_SLUG, "Bearer jwt");

        assertEquals(new ModuleTokenResult.Expired(MODULE_SLUG), result);
    }

    @Test
    void freePlanNotActivatedResolutionIsMapped() {
        when(subscriptionServiceClient.resolveModuleAccess(any(), any(), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("FREE_PLAN_NOT_ACTIVATED", moduleId.toString(), "PDF",
                        null, null, null, "plan-version-id"));

        var result = service.issue(userId, tenantId, "owner", MODULE_SLUG, "Bearer jwt");

        assertEquals(new ModuleTokenResult.FreePlanNotActivated(MODULE_SLUG, moduleId.toString(), "plan-version-id"), result);
    }

    @Test
    void noAccessResolutionIsMapped() {
        when(subscriptionServiceClient.resolveModuleAccess(any(), any(), eq(MODULE_SLUG)))
                .thenReturn(new ModuleAccessResolution("NO_ACCESS", moduleId.toString(), "PDF", null, null, null, null));

        var result = service.issue(userId, tenantId, "owner", MODULE_SLUG, "Bearer jwt");

        assertEquals(new ModuleTokenResult.NoAccess(MODULE_SLUG), result);
    }
}
