package com.saas.auth.resource;

import com.saas.auth.negocio.ModuleTokenResult;
import com.saas.auth.negocio.impl.ModuleTokenNegocio;
import com.saas.platformtenant.TenantContext;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Map;

/**
 * Gera o ModuleAccessToken (MAT) quando o usuário entra em um módulo.
 *
 * Autenticação: Supabase JWT + X-Tenant-ID (via TenantResolutionFilter).
 * O MAT carrega apenas permissões e limites do módulo solicitado.
 * Validade curta (padrão 30 min), renovável pelo frontend.
 *
 * A checagem de assinatura/plano (profile_module_subscriptions/
 * plan_version_modules/plan_version_module_limits) é resolvida pelo
 * subscription-service via ModuleTokenNegocio — este Resource só traduz o
 * resultado da emissão em status HTTP.
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
    ModuleTokenNegocio moduleTokenNegocio;

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
    public Response generate(
            @PathParam("moduleSlug") String moduleSlug,
            @Context SecurityContext secCtx,
            @Context HttpHeaders httpHeaders
    ) {
        var ctx = TenantContext.from(secCtx);
        String authorization = httpHeaders.getHeaderString(HttpHeaders.AUTHORIZATION);

        ModuleTokenResult result = moduleTokenNegocio.issue(
                ctx.getUserId(), ctx.getTenantId(), ctx.getUserRole(), moduleSlug, authorization);

        return switch (result) {
            case ModuleTokenResult.Issued r -> Response.ok(r.response()).build();

            case ModuleTokenResult.NotFound r -> Response.status(404).entity(Map.of(
                    "error", "Módulo não encontrado: " + r.moduleSlug()
            )).build();

            case ModuleTokenResult.Expired r -> Response.status(403).entity(Map.of(
                    "code", "MODULE_EXPIRED",
                    "error", "Assinatura deste módulo expirou",
                    "moduleSlug", r.moduleSlug()
            )).build();

            case ModuleTokenResult.FreePlanNotActivated r -> Response.status(409).entity(Map.of(
                    "code", "FREE_PLAN_NOT_ACTIVATED",
                    "error", "Plano gratuito disponível para este módulo, mas ainda não ativado",
                    "moduleSlug", r.moduleSlug(),
                    "moduleId", r.moduleId(),
                    "planVersionId", r.planVersionId()
            )).build();

            case ModuleTokenResult.NoAccess r -> Response.status(403).entity(Map.of(
                    "error", "Acesso negado ao módulo: sem assinatura ativa nem plano gratuito",
                    "moduleSlug", r.moduleSlug()
            )).build();
        };
    }
}
