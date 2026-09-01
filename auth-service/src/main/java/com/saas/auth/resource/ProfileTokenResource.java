package com.saas.auth.resource;

import com.saas.auth.dto.PermissionsVersionResponse;
import com.saas.auth.negocio.impl.ProfileTokenNegocio;
import com.saas.auth.security.TenantContext;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

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
    ProfileTokenNegocio profileTokenNegocio;

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
    public Response generate(@Context SecurityContext secCtx) {
        var ctx = TenantContext.from(secCtx);
        var response = profileTokenNegocio.issueAccessToken(ctx.getUserId(), ctx.getTenantId(), ctx.getUserRole());
        return Response.ok(response).build();
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
        int version = profileTokenNegocio.permissionsVersion(ctx.getUserId(), ctx.getTenantId());
        return Response.ok(new PermissionsVersionResponse(version)).build();
    }
}
