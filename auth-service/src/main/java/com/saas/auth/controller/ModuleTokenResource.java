package com.saas.auth.controller;

import com.saas.auth.client.SubscriptionServiceClient;
import com.saas.auth.repository.UserTenantRepository;
import com.saas.auth.security.TenantContext;
import com.saas.auth.security.TokenService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gera o ModuleAccessToken (MAT) quando o usuário entra em um módulo.
 *
 * Autenticação: Supabase JWT + X-Tenant-ID (via TenantResolutionFilter).
 * O MAT carrega apenas permissões e limites do módulo solicitado.
 * Validade curta (padrão 30 min), renovável pelo frontend.
 *
 * A checagem de assinatura/plano (profile_module_subscriptions/
 * plan_version_modules/plan_version_module_limits) é resolvida pelo
 * subscription-service via SubscriptionServiceClient — este serviço apenas
 * carrega as permissões de módulo do usuário (domínio de perfil/nível de
 * acesso, não de assinatura) e assina o token com os claims já resolvidos.
 */
@Path("/api/v1/module-token/{moduleSlug}")
@Tag(name = "Tokens", description = "Emissão de tokens internos com escopo de módulo (ModuleAccessToken).")
@Authenticated
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT emitido pelo Supabase Auth (login do usuário). Enviar como 'Authorization: Bearer <token>'."
)
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ModuleTokenResource {

    @Inject
    EntityManager em;

    @Inject
    TokenService tokenService;

    @Inject
    UserTenantRepository userTenantRepository;

    @Inject
    @RestClient
    SubscriptionServiceClient subscriptionServiceClient;

    @POST
    @Operation(
        summary = "Emite o ModuleAccessToken (MAT) de um módulo para o tenant ativo",
        description = "Emite o token usado para autorizar chamadas ao serviço do módulo " +
            "`moduleSlug` (ex.: pdf, whatsapp). Primeiro consulta o subscription-service " +
            "para resolver o acesso do tenant ao módulo (assinatura ativa, plano gratuito " +
            "ativado, limites do plano); se concedido, carrega as permissões de serviço do " +
            "usuário dentro do módulo (owner/admin recebem todos os serviços ativos; membros " +
            "recebem apenas os do seu nível de acesso) e assina o token com esses claims já " +
            "resolvidos. Validade curta (30 minutos), renovável pelo frontend chamando este " +
            "endpoint novamente. Este serviço não decide sozinho se o tenant tem direito ao " +
            "módulo — essa regra pertence ao subscription-service."
    )
    @APIResponse(responseCode = "200", description = "Token emitido com sucesso, junto com permissões, limites do plano e data de expiração.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Assinatura do módulo expirada (`MODULE_EXPIRED`) ou tenant sem qualquer acesso ao módulo (`NO_ACCESS`).")
    @APIResponse(responseCode = "404", description = "`moduleSlug` não corresponde a nenhum módulo existente no catálogo.")
    @APIResponse(responseCode = "409", description = "Módulo possui plano gratuito disponível, mas o tenant ainda não o ativou (`FREE_PLAN_NOT_ACTIVATED`).")
    @SuppressWarnings("unchecked")
    public Response generate(
            @PathParam("moduleSlug") String moduleSlug,
            @Context SecurityContext secCtx,
            @Context HttpHeaders httpHeaders
    ) {
        var ctx = TenantContext.from(secCtx);
        UUID userId   = ctx.getUserId();
        UUID tenantId = ctx.getTenantId();
        String role   = ctx.getUserRole();

        String authorization = httpHeaders.getHeaderString(HttpHeaders.AUTHORIZATION);
        Map<String, Object> access;
        try (Response upstream = subscriptionServiceClient.resolveModuleAccess(
                authorization, tenantId.toString(), moduleSlug)) {
            access = upstream.readEntity(Map.class);
        }

        String resolution = (String) access.get("resolution");
        switch (resolution) {
            case "MODULE_NOT_FOUND":
                return Response.status(404).entity(Map.of("error", "Módulo não encontrado: " + moduleSlug)).build();
            case "MODULE_EXPIRED":
                return Response.status(403).entity(Map.of(
                        "code", "MODULE_EXPIRED",
                        "error", "Assinatura deste módulo expirou",
                        "moduleSlug", moduleSlug
                )).build();
            case "FREE_PLAN_NOT_ACTIVATED":
                return Response.status(409).entity(Map.of(
                        "code", "FREE_PLAN_NOT_ACTIVATED",
                        "error", "Plano gratuito disponível para este módulo, mas ainda não ativado",
                        "moduleSlug", moduleSlug,
                        "moduleId", access.get("moduleId"),
                        "planVersionId", access.get("planVersionId")
                )).build();
            case "NO_ACCESS":
                return Response.status(403).entity(Map.of(
                        "error", "Acesso negado ao módulo: sem assinatura ativa nem plano gratuito",
                        "moduleSlug", moduleSlug
                )).build();
            default:
                // "GRANTED" segue para emissão do token abaixo.
        }

        String moduleId     = (String) access.get("moduleId");
        String moduleName   = (String) access.get("moduleName");
        String planName     = (String) access.get("planName");
        String accessSource = (String) access.get("accessSource");
        Map<String, Object> limits = (Map<String, Object>) access.get("limits");

        // Carrega permissões do módulo para o usuário (domínio de perfil/nível de
        // acesso — não muda com a migração da checagem de assinatura/plano).
        List<String> permissions = loadModulePermissions(userId, tenantId, moduleId, moduleSlug, role);

        // Versão de permissões para invalidação
        int permissionsVersion = userTenantRepository.resolvePermissionsVersion(userId, tenantId);

        long expiryMinutes = 30;
        Instant expiresAt = Instant.now().plusSeconds(expiryMinutes * 60);

        String token = tokenService.generateModuleToken(
                userId,
                tenantId,
                moduleId,
                moduleSlug,
                planName,
                accessSource,
                permissions,
                limits,
                permissionsVersion
        );

        return Response.ok(Map.of(
                "moduleAccessToken", token,
                "moduleSlug",        moduleSlug,
                "moduleName",        moduleName,
                "planName",          planName != null ? planName : "",
                "expiresAt",         expiresAt.toString(),
                "permissions",       permissions,
                "limits",            limits
        )).build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String> loadModulePermissions(UUID userId, UUID tenantId,
                                                String moduleId, String moduleSlug, String role) {
        // moduleSlug já é conhecido pelo token (claim moduleSlug); a permissão
        // carrega apenas o identificador do serviço dentro do módulo.
        List<String> permissions = new ArrayList<>();

        if (List.of("owner", "admin").contains(role)) {
            // Owner/admin tem acesso a todos os serviços do módulo
            List<String> svcRows = em.createNativeQuery("""
                SELECT s.slug
                FROM platform_module_services s
                WHERE s.module_id = :moduleId AND s.is_active = TRUE
            """).setParameter("moduleId", UUID.fromString(moduleId)).getResultList();

            permissions.addAll(svcRows);
        } else {
            // Membro: filtra pelos serviços do nível de acesso
            List<String> svcRows = em.createNativeQuery("""
                SELECT s.slug
                FROM profile_access_level_permissions palp
                JOIN user_tenants ut ON ut.access_level_id = palp.access_level_id
                JOIN platform_module_services s ON s.id = palp.service_id
                WHERE ut.user_id = :userId
                  AND ut.tenant_id = :tenantId
                  AND ut.is_active = TRUE
                  AND palp.module_id = :moduleId
                  AND s.is_active = TRUE
            """)
            .setParameter("userId", userId)
            .setParameter("tenantId", tenantId)
            .setParameter("moduleId", UUID.fromString(moduleId))
            .getResultList();

            permissions.addAll(svcRows);
        }

        return permissions;
    }

}
