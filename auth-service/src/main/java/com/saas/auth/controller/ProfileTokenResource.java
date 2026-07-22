package com.saas.auth.controller;

import com.saas.auth.repository.UserTenantRepository;
import com.saas.auth.security.TenantContext;
import com.saas.auth.security.TokenService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gera o ProfileAccessToken (PAT) após o usuário selecionar um perfil.
 *
 * Autenticação: Supabase JWT + X-Tenant-ID (via TenantResolutionFilter).
 * O PAT carrega apenas permissões gerenciais do perfil, não permissões internas de módulos.
 */
@Path("/api/v1/profile")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileTokenResource {

    @Inject
    EntityManager em;

    @Inject
    TokenService tokenService;

    @Inject
    UserTenantRepository userTenantRepository;

    @POST
    @Path("/access-token")
    @SuppressWarnings("unchecked")
    public Response generate(@Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        UUID userId   = ctx.getUserId();
        UUID tenantId = ctx.getTenantId();
        String role   = ctx.getUserRole();

        // Tipo do perfil: INDIVIDUAL ou COMPANY (derivado da coluna type do tenant)
        String profileType = resolveProfileType(tenantId);

        // Versão de permissões do membro (para invalidação de token ao alterar permissões)
        int permissionsVersion = userTenantRepository.resolvePermissionsVersion(userId, tenantId);

        // Nível de acesso (apenas para membros)
        String accessLevelId = null;

        // Permissões administrativas do perfil
        List<String> permissions = new ArrayList<>();

        if (List.of("owner", "admin").contains(role)) {
            // Owner/admin tem todas as permissões administrativas
            permissions = buildOwnerPermissions();
        } else {
            // Membro: carrega permissões do nível de acesso
            List<Object[]> accessLevelRows = em.createNativeQuery("""
                SELECT ut.access_level_id::text,
                       ap.permission_key
                FROM user_tenants ut
                LEFT JOIN profile_access_level_admin_permissions ap
                  ON ap.access_level_id = ut.access_level_id
                WHERE ut.user_id = :userId
                  AND ut.tenant_id = :tenantId
                  AND ut.is_active = TRUE
            """)
            .setParameter("userId", userId)
            .setParameter("tenantId", tenantId)
            .getResultList();

            for (Object[] row : accessLevelRows) {
                if (accessLevelId == null && row[0] != null) {
                    accessLevelId = (String) row[0];
                }
                if (row[1] != null) {
                    permissions.add("profile." + row[1]);
                }
            }
            // Todos os membros podem acessar o dashboard
            if (!permissions.contains("profile.dashboard.view")) {
                permissions.add("profile.dashboard.view");
            }
        }

        long expiryHours = 8;
        Instant expiresAt = Instant.now().plusSeconds(expiryHours * 3600);

        String token = tokenService.generateProfileToken(
                userId,
                tenantId,
                profileType,
                toProfileRole(role),
                accessLevelId,
                permissions,
                permissionsVersion
        );

        return Response.ok(java.util.Map.of(
                "profileAccessToken", token,
                "expiresAt",          expiresAt.toString(),
                "permissions",        permissions
        )).build();
    }

    // ─── GET /permissions-version — polling leve para invalidação no frontend ─────

    /**
     * Retorna a versão vigente de permissões do perfil ativo, para o frontend
     * detectar (via polling curto) mudanças de nível de acesso/permissões/assinatura
     * e descartar o ProfileAccessToken/ModuleAccessToken em cache sem esperar expirar.
     */
    @GET
    @Path("/permissions-version")
    public Response permissionsVersion(@Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        int version = userTenantRepository.resolvePermissionsVersion(ctx.getUserId(), ctx.getTenantId());
        return Response.ok(java.util.Map.of("permissionsVersion", version)).build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private String resolveProfileType(UUID tenantId) {
        try {
            String type = (String) em.createNativeQuery(
                "SELECT type FROM tenants WHERE id = :id"
            ).setParameter("id", tenantId).getSingleResult();
            return "individual".equals(type) ? "INDIVIDUAL" : "COMPANY";
        } catch (Exception e) {
            return "COMPANY";
        }
    }

    private String toProfileRole(String role) {
        return switch (role) {
            case "owner"      -> "OWNER";
            case "admin"      -> "OWNER";
            case "member"     -> "MEMBER";
            case "individual" -> "INDIVIDUAL";
            default           -> "MEMBER";
        };
    }

    private List<String> buildOwnerPermissions() {
        return List.of(
                "profile.dashboard.view",
                "profile.members.view",
                "profile.members.invite",
                "profile.members.remove",
                "profile.members.change_access_level",
                "profile.access_levels.view",
                "profile.access_levels.create",
                "profile.access_levels.edit",
                "profile.access_levels.inactivate",
                "profile.access_levels.delete",
                "profile.plans.view",
                "profile.plans.subscribe",
                "profile.subscriptions.view",
                "profile.subscriptions.cancel",
                "profile.subscriptions.reactivate",
                "profile.company_settings.view",
                "profile.company_settings.edit",
                "profile.invites.view",
                "profile.invites.cancel",
                "profile.invites.resend",
                "profile.billing.view",
                "profile.billing.payment_methods.manage",
                "profile.billing.payment_history.view"
        );
    }
}
