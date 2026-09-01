package com.saas.subscription.resource;

import com.saas.subscription.security.TenantContext;
import com.saas.subscription.negocio.impl.ModuleAccessNegocio;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Resolução de acesso a módulo (assinatura/plano/limites) para consumo
 * interno do auth-service ao emitir o Module Access Token. Consolida a
 * lógica que antes vivia em auth-service.ModuleTokenResource — planos e
 * assinaturas são domínio deste serviço; auth-service deve apenas assinar
 * o token com os claims já resolvidos aqui.
 *
 * Chamado serviço-a-serviço repassando o mesmo header Authorization (JWT
 * Supabase) e X-Tenant-ID recebidos pelo auth-service do frontend — resolve
 * o tenant via o mesmo TenantResolutionFilter usado pelos demais endpoints
 * deste serviço, sem exigir um contrato de autenticação separado.
 *
 * Sempre responde 200 com um campo "resolution" indicando o resultado
 * (GRANTED / MODULE_NOT_FOUND / MODULE_EXPIRED / FREE_PLAN_NOT_ACTIVATED /
 * NO_ACCESS) — quem decide o status HTTP exposto ao frontend é o
 * auth-service, que já tinha esse contrato antes da migração.
 */
@Path("/api/v1/internal/module-access")
@Tag(name = "Module Access", description = "Resolução interna, serviço-a-serviço, de acesso a módulo (assinatura/plano/limites) consumida pelo auth-service ao emitir o ModuleAccessToken. Não é chamada diretamente pelo frontend.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
public class ModuleAccessResource {

    @Inject
    ModuleAccessNegocio moduleAccessNegocio;

    @GET
    @Path("/{moduleSlug}")
    @Operation(
        summary = "Resolve o acesso do tenant ativo a um módulo (uso interno do auth-service)",
        description = "Endpoint interno, chamado pelo auth-service repassando o mesmo Authorization " +
            "(JWT Supabase) e X-Tenant-ID recebidos do frontend — o tenant é resolvido pelo mesmo " +
            "TenantResolutionFilter usado pelas rotas tenant-scoped deste serviço. Avalia o acesso " +
            "em ordem de especificidade: (1) assinatura ativa/trial em profile_module_subscriptions; " +
            "(2) plano gratuito do módulo ainda não ativado; (3) fallback legado via feature set da " +
            "assinatura principal do tenant (TenantContext). Sempre responde HTTP 200 com um campo " +
            "`resolution`; quem traduz esse resultado em status HTTP para o frontend é o " +
            "auth-service (ver ModuleTokenResource), não este endpoint. Valores possíveis de " +
            "`resolution`: GRANTED (acesso concedido, com `limits` do plano), MODULE_NOT_FOUND " +
            "(slug não corresponde a módulo ativo), MODULE_EXPIRED (assinatura existente porém " +
            "vencida), FREE_PLAN_NOT_ACTIVATED (há plano gratuito mas o tenant ainda não o ativou), " +
            "NO_ACCESS (nenhuma das condições anteriores é satisfeita)."
    )
    @APIResponse(responseCode = "200", description = "Resolução concluída; o campo `resolution` no corpo indica o resultado (GRANTED, MODULE_NOT_FOUND, MODULE_EXPIRED, FREE_PLAN_NOT_ACTIVATED ou NO_ACCESS). Este endpoint não usa outros códigos HTTP para representar esses casos.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido, ou tenant não resolvido a partir do header X-Tenant-ID.")
    public Response resolve(
        @Parameter(description = "Slug do módulo cujo acesso está sendo resolvido (ex.: pdf, whatsapp).", required = true)
        @PathParam("moduleSlug") String moduleSlug, @Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        return Response.ok(moduleAccessNegocio.resolve(moduleSlug, ctx)).build();
    }
}
