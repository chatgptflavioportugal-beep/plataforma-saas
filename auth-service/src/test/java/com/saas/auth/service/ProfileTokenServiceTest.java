package com.saas.auth.service;

import com.saas.auth.repository.AccessLevelPermissionRepository;
import com.saas.auth.repository.AccessLevelPermissionRepository.AccessLevelPermission;
import com.saas.auth.repository.TenantRepository;
import com.saas.auth.repository.UserTenantRepository;
import com.saas.auth.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileTokenServiceTest {

    private TenantRepository tenantRepository;
    private UserTenantRepository userTenantRepository;
    private AccessLevelPermissionRepository accessLevelPermissionRepository;
    private TokenService tokenService;
    private ProfileTokenService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        userTenantRepository = mock(UserTenantRepository.class);
        accessLevelPermissionRepository = mock(AccessLevelPermissionRepository.class);
        tokenService = mock(TokenService.class);

        service = new ProfileTokenService();
        service.tenantRepository = tenantRepository;
        service.userTenantRepository = userTenantRepository;
        service.accessLevelPermissionRepository = accessLevelPermissionRepository;
        service.tokenService = tokenService;

        when(tenantRepository.findType(tenantId)).thenReturn(Optional.of("company"));
        when(userTenantRepository.resolvePermissionsVersion(userId, tenantId)).thenReturn(3);
        when(tokenService.generateProfileToken(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new TokenService.IssuedToken("signed-token", Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void ownerReceivesAllOwnerPermissionsAndNoAccessLevel() {
        var response = service.issueAccessToken(userId, tenantId, "owner");

        assertEquals("signed-token", response.profileAccessToken());
        assertEquals("2026-01-01T00:00:00Z", response.expiresAt());
        assertTrue(response.permissions().contains("profile.plans.subscribe"));
        assertTrue(response.permissions().contains("profile.dashboard.view"));

        verify(tokenService).generateProfileToken(
                eq(userId), eq(tenantId), eq("COMPANY"), eq("OWNER"), isNull(), anyList(), eq(3));
    }

    @Test
    void memberReceivesOnlyAccessLevelPermissionsPlusDashboard() {
        String accessLevelId = UUID.randomUUID().toString();
        when(accessLevelPermissionRepository.findAdminPermissions(userId, tenantId)).thenReturn(List.of(
                new AccessLevelPermission(accessLevelId, "members.view"),
                new AccessLevelPermission(accessLevelId, "billing.view")
        ));

        var response = service.issueAccessToken(userId, tenantId, "member");

        assertTrue(response.permissions().contains("profile.members.view"));
        assertTrue(response.permissions().contains("profile.billing.view"));
        assertTrue(response.permissions().contains("profile.dashboard.view"));
        assertFalse(response.permissions().contains("profile.plans.subscribe"));

        verify(tokenService).generateProfileToken(
                eq(userId), eq(tenantId), eq("COMPANY"), eq("MEMBER"), eq(accessLevelId), anyList(), eq(3));
    }

    @Test
    void resolvesIndividualProfileType() {
        when(tenantRepository.findType(tenantId)).thenReturn(Optional.of("individual"));

        service.issueAccessToken(userId, tenantId, "individual");

        verify(tokenService).generateProfileToken(
                eq(userId), eq(tenantId), eq("INDIVIDUAL"), eq("INDIVIDUAL"), isNull(), anyList(), eq(3));
    }

    @Test
    void defaultsToCompanyWhenTenantNotFound() {
        when(tenantRepository.findType(tenantId)).thenReturn(Optional.empty());

        service.issueAccessToken(userId, tenantId, "owner");

        verify(tokenService).generateProfileToken(
                eq(userId), eq(tenantId), eq("COMPANY"), eq("OWNER"), isNull(), anyList(), eq(3));
    }

    @Test
    void permissionsVersionDelegatesToRepository() {
        when(userTenantRepository.resolvePermissionsVersion(userId, tenantId)).thenReturn(7);

        assertEquals(7, service.permissionsVersion(userId, tenantId));
    }
}
