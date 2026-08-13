package com.saas.auth.service;

import com.saas.auth.client.ModuleAccessResolution;
import com.saas.auth.client.SubscriptionServiceClient;
import com.saas.auth.dto.ModuleTokenResponse;
import com.saas.auth.repository.ModuleServiceRepository;
import com.saas.auth.repository.UserTenantRepository;
import com.saas.auth.security.TokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orquestra a emissão do ModuleAccessToken (MAT): resolve o acesso do tenant
 * ao módulo no subscription-service (assinatura/plano/limites — domínio
 * daquele serviço), carrega as permissões de serviço do usuário dentro do
 * módulo (domínio de perfil/nível de acesso, deste serviço) e assina o token
 * com os claims já resolvidos.
 */
@ApplicationScoped
public class ModuleTokenService {

    @Inject
    @RestClient
    SubscriptionServiceClient subscriptionServiceClient;

    @Inject
    ModuleServiceRepository moduleServiceRepository;

    @Inject
    UserTenantRepository userTenantRepository;

    @Inject
    TokenService tokenService;

    public ModuleTokenResult issue(UUID userId, UUID tenantId, String role,
                                    String moduleSlug, String authorization) {
        ModuleAccessResolution access = subscriptionServiceClient.resolveModuleAccess(
                authorization, tenantId.toString(), moduleSlug);

        return switch (access.resolution()) {
            case "MODULE_NOT_FOUND" -> new ModuleTokenResult.NotFound(moduleSlug);
            case "MODULE_EXPIRED" -> new ModuleTokenResult.Expired(moduleSlug);
            case "FREE_PLAN_NOT_ACTIVATED" -> new ModuleTokenResult.FreePlanNotActivated(
                    moduleSlug, access.moduleId(), access.planVersionId());
            case "NO_ACCESS" -> new ModuleTokenResult.NoAccess(moduleSlug);
            default -> new ModuleTokenResult.Issued(buildResponse(userId, tenantId, role, moduleSlug, access));
        };
    }

    private ModuleTokenResponse buildResponse(UUID userId, UUID tenantId, String role,
                                               String moduleSlug, ModuleAccessResolution access) {
        UUID moduleId = UUID.fromString(access.moduleId());
        List<String> permissions = loadModulePermissions(userId, tenantId, moduleId, role);
        int permissionsVersion = userTenantRepository.resolvePermissionsVersion(userId, tenantId);

        var issued = tokenService.generateModuleToken(
                userId,
                tenantId,
                access.moduleId(),
                moduleSlug,
                access.planName(),
                access.accessSource(),
                permissions,
                access.limits(),
                permissionsVersion
        );

        return new ModuleTokenResponse(
                issued.token(),
                moduleSlug,
                access.moduleName(),
                access.planName() != null ? access.planName() : "",
                issued.expiresAt().toString(),
                permissions,
                access.limits()
        );
    }

    /** Owner/admin recebem todos os serviços ativos do módulo; membros, apenas os do seu nível de acesso. */
    private List<String> loadModulePermissions(UUID userId, UUID tenantId, UUID moduleId, String role) {
        if (List.of("owner", "admin").contains(role)) {
            return new ArrayList<>(moduleServiceRepository.findActiveServiceSlugsByModule(moduleId));
        }
        return new ArrayList<>(moduleServiceRepository.findServiceSlugsByAccessLevel(userId, tenantId, moduleId));
    }
}
