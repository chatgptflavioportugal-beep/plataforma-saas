package com.saas.auth.controller;

import com.saas.auth.repository.UserTenantRepository;
import com.saas.auth.security.TenantContext;
import com.saas.auth.security.TokenService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
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
@Tag(name = "Tokens", description = "Emissão de tokens internos com escopo de perfil/tenant (ProfileAccessToken).")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
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
    @Operation(
        summary = "Emite o ProfileAccessToken (PAT) do perfil/tenant selecionado",
        description = "Gera o ProfileAccessToken a partir do JWT do Supabase e do tenant " +
            "resolvido pelo header X-Tenant-ID. O PAT carrega o papel do usuário no tenant " +
            "(OWNER/MEMBER/INDIVIDUAL), a versão de permissões vigente e a lista de " +
            "permissões administrativas do perfil (dono/admin recebem todas; membros " +
            "recebem as do seu nível de acesso). Tem validade de 8 horas e é usado pelo " +
            "frontend para as telas de gestão do perfil (membros, níveis de acesso, " +
            "planos, configurações da empresa) — não concede acesso a nenhum módulo."
    )
    @APIResponse(responseCode = "200", description = "Token emitido com sucesso, junto com a lista de permissões e a data de expiração.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente, inválido ou expirado.")
    @APIResponse(responseCode = "400", description = "Header X-Tenant-ID ausente ou tenant não resolvido para o usuário autenticado.")
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
    @Operation(
        summary = "Consulta a versão vigente de permissões do perfil ativo",
        description = "Operação exclusivamente de consulta, usada pelo frontend em polling " +
            "curto para detectar mudanças de nível de acesso, permissões ou assinatura sem " +
            "esperar o PAT/MAT expirarem naturalmente. Quando o número retornado difere do " +
            "valor embutido no token em cache, o frontend deve descartá-lo e solicitar um " +
            "novo ProfileAccessToken/ModuleAccessToken."
    )
    @APIResponse(responseCode = "200", description = "Versão de permissões atual do vínculo usuário-tenant.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente, inválido ou expirado.")
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
