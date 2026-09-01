package com.saas.profile.resource;

import com.saas.profile.dto.request.AccessLevelRequest;
import com.saas.profile.dto.request.UpdateAccessLevelStatusRequest;
import com.saas.profile.dto.response.CreatedIdResponse;
import com.saas.profile.dto.response.StatusResponse;
import com.saas.profile.dto.response.SuccessResponse;
import com.saas.profile.security.TenantContext;
import com.saas.profile.negocio.impl.AccessLevelNegocio;
import com.saas.profile.negocio.impl.AdminAccessNegocio;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

/**
 * Níveis de acesso: papéis customizados por tenant, com permissões granulares
 * por serviço de módulo assinado e por um conjunto fixo de permissões
 * administrativas do próprio profile-service (catálogo em AccessLevelNegocio).
 *
 * Todas as rotas exigem Supabase JWT + X-Tenant-ID resolvido pelo
 * TenantResolutionFilter (o {tenantId} do path precisa coincidir com o tenant
 * resolvido) e, além de owner/admin, membros só passam quando o nível de
 * acesso vinculado a eles contém a permissão administrativa correspondente.
 */
@Path("/api/v1/tenants/{tenantId}/access-levels")
@Tag(name = "Access Levels", description = "Gestão de níveis de acesso (papéis customizados) e das permissões granulares por módulo/serviço e administrativas de um tenant.")
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
public class AccessLevelResource {

    @Inject
    AccessLevelNegocio accessLevelService;

    @Inject
    AdminAccessNegocio adminAccessService;

    // ─── GET /available-modules — árvore completa (módulos + grupos + permissões adm.) ──

    @GET
    @Path("/available-modules")
    @Operation(
        summary = "Lista a árvore de módulos/grupos/serviços disponível para montar um nível de acesso",
        description = "Operação exclusivamente de consulta, usada pela tela de criação/edição " +
            "de nível de acesso para montar a árvore de seleção de permissões. Retorna apenas " +
            "módulos com assinatura ATIVA do tenant e seus serviços ativos, agrupados pelo " +
            "grupo de serviço (service group) quando este existir e estiver com status ACTIVE " +
            "— serviços cujo grupo esteja inativo aparecem soltos em `ungroupedServices` do " +
            "módulo. Também retorna, em `adminPermissions`, o catálogo fixo (hardcoded no " +
            "código) de permissões administrativas do profile-service (membros, níveis de " +
            "acesso, planos, assinaturas, configurações da empresa, dashboard, convites, " +
            "faturamento), que não dependem de módulo assinado."
    )
    @APIResponse(responseCode = "200", description = "Árvore de módulos/grupos/serviços assinados e ativos, junto com o catálogo de permissões administrativas.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID.")
    public Response availableModules(
        @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
        @PathParam("tenantId") UUID tenantId,
        @Context SecurityContext ctx
    ) {
        TenantContext.resolveAndCheck(ctx, tenantId);
        return Response.ok(accessLevelService.availableModules(tenantId)).build();
    }

    // ─── GET / — listar níveis de acesso ─────────────────────────────────────

    @GET
    @Operation(
        summary = "Lista os níveis de acesso cadastrados no tenant",
        description = "Retorna todos os níveis de acesso do tenant (ativos e inativos), cada " +
            "um com suas permissões de serviço de módulo, permissões administrativas, e a " +
            "contagem de membros atualmente vinculados a ele (`memberCount`, apenas membros " +
            "ativos). Requer a permissão administrativa `access_levels.view` — concedida " +
            "automaticamente a owner/admin, ou explicitamente ao nível de acesso do membro."
    )
    @APIResponse(responseCode = "200", description = "Lista de níveis de acesso do tenant, com permissões e contagem de membros de cada um.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID, ou o usuário não possui a permissão `access_levels.view`.")
    public Response listAccessLevels(
        @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
        @PathParam("tenantId") UUID tenantId,
        @Context SecurityContext ctx
    ) {
        TenantContext tc = TenantContext.resolveAndCheck(ctx, tenantId);
        adminAccessService.requireAdminPerm(tc, tenantId, "access_levels.view");
        return Response.ok(accessLevelService.listAccessLevels(tenantId)).build();
    }

    // ─── POST / — criar nível de acesso ──────────────────────────────────────

    @POST
    @Operation(
        summary = "Cria um novo nível de acesso no tenant",
        description = "Cria o nível de acesso com `name` (obrigatório) e `description` " +
            "opcional, e associa a ele, opcionalmente, uma lista de `serviceIds` (serviços de " +
            "módulos assinados e ativos pelo tenant — cada serviço inválido ou não disponível " +
            "para o tenant rejeita a requisição inteira) e/ou `adminPermissionKeys` (chaves do " +
            "catálogo fixo de permissões administrativas — cada chave desconhecida rejeita a " +
            "requisição inteira). Requer a permissão administrativa `access_levels.create`."
    )
    @APIResponse(responseCode = "201", description = "Nível de acesso criado; retorna o `id` gerado.")
    @APIResponse(responseCode = "400", description = "`name` ausente/em branco, ou algum `serviceId`/`adminPermissionKey` informado é inválido, indisponível para o tenant, ou desconhecido.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID, ou o usuário não possui a permissão `access_levels.create`.")
    public Response createAccessLevel(
        @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
        @PathParam("tenantId") UUID tenantId,
        AccessLevelRequest body,
        @Context SecurityContext ctx
    ) {
        TenantContext tc = TenantContext.resolveAndCheck(ctx, tenantId);
        adminAccessService.requireAdminPerm(tc, tenantId, "access_levels.create");
        String levelId = accessLevelService.createAccessLevel(tenantId, body);
        return Response.status(201).entity(new CreatedIdResponse(levelId)).build();
    }

    // ─── PUT /{alId} — editar nível de acesso ────────────────────────────────

    @PUT
    @Path("/{alId}")
    @Operation(
        summary = "Substitui nome, descrição e permissões de um nível de acesso",
        description = "Atualiza `name` (obrigatório) e `description`, e substitui " +
            "completamente o conjunto de permissões de serviço e de permissões " +
            "administrativas do nível pelas listas `serviceIds`/`adminPermissionKeys` " +
            "informadas no corpo (as permissões atuais são removidas antes de inserir as " +
            "novas; omitir uma lista equivale a zerar aquele tipo de permissão). Ao final, " +
            "incrementa a versão de permissões (`permissions_version`) de todos os membros " +
            "vinculados a este nível, invalidando qualquer ProfileAccessToken/ModuleAccessToken " +
            "em cache deles. Requer a permissão administrativa `access_levels.edit`."
    )
    @APIResponse(responseCode = "200", description = "Nível de acesso atualizado com sucesso.")
    @APIResponse(responseCode = "400", description = "`name` ausente/em branco, ou algum `serviceId`/`adminPermissionKey` informado é inválido, indisponível para o tenant, ou desconhecido.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID, ou o usuário não possui a permissão `access_levels.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhum nível de acesso com o `alId` informado existe para este tenant.")
    public Response updateAccessLevel(
        @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
        @PathParam("tenantId") UUID tenantId,
        @Parameter(description = "Identificador do nível de acesso a ser atualizado.", required = true)
        @PathParam("alId") UUID alId,
        AccessLevelRequest body,
        @Context SecurityContext ctx
    ) {
        TenantContext tc = TenantContext.resolveAndCheck(ctx, tenantId);
        adminAccessService.requireAdminPerm(tc, tenantId, "access_levels.edit");
        accessLevelService.updateAccessLevel(tenantId, alId, body);
        return Response.ok(SuccessResponse.OK).build();
    }

    // ─── PATCH /{alId}/status — ativar/inativar ───────────────────────────────

    @PATCH
    @Path("/{alId}/status")
    @Operation(
        summary = "Ativa ou inativa um nível de acesso",
        description = "Altera o `status` do nível de acesso para `ACTIVE` ou `INACTIVE` " +
            "(único valor aceito no corpo, chave `status`). Um nível inativo continua " +
            "existindo e mantendo suas permissões, mas deixa de poder ser atribuído a novos " +
            "membros/convites (a validação fica a cargo de InvitationNegocio/AccessLevelResource " +
            "ao referenciar o nível). Ao final, incrementa a versão de permissões dos membros " +
            "vinculados a este nível, invalidando tokens em cache. Requer a permissão " +
            "administrativa `access_levels.inactivate`."
    )
    @APIResponse(responseCode = "200", description = "Status alterado com sucesso; retorna o novo `status`.")
    @APIResponse(responseCode = "400", description = "`status` ausente ou diferente de ACTIVE/INACTIVE.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID, ou o usuário não possui a permissão `access_levels.inactivate`.")
    @APIResponse(responseCode = "404", description = "Nenhum nível de acesso com o `alId` informado existe para este tenant.")
    public Response toggleStatus(
        @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
        @PathParam("tenantId") UUID tenantId,
        @Parameter(description = "Identificador do nível de acesso.", required = true)
        @PathParam("alId") UUID alId,
        UpdateAccessLevelStatusRequest body,
        @Context SecurityContext ctx
    ) {
        TenantContext tc = TenantContext.resolveAndCheck(ctx, tenantId);
        adminAccessService.requireAdminPerm(tc, tenantId, "access_levels.inactivate");
        String status = accessLevelService.updateStatus(tenantId, alId, body != null ? body.status() : null);
        return Response.ok(new StatusResponse(true, status)).build();
    }

    // ─── DELETE /{alId} — excluir (somente se não estiver em uso) ─────────────

    @DELETE
    @Path("/{alId}")
    @Operation(
        summary = "Exclui um nível de acesso não utilizado",
        description = "Remove definitivamente o nível de acesso e suas permissões associadas. " +
            "A exclusão só é permitida se não houver nenhum membro ativo vinculado ao nível " +
            "nem nenhum convite pendente referenciando-o — nesses casos a operação é recusada " +
            "e o chamador deve reatribuir os membros/convites antes de tentar novamente. " +
            "Requer a permissão administrativa `access_levels.delete`."
    )
    @APIResponse(responseCode = "204", description = "Nível de acesso excluído com sucesso.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID, ou o usuário não possui a permissão `access_levels.delete`.")
    @APIResponse(responseCode = "404", description = "Nenhum nível de acesso com o `alId` informado existe para este tenant.")
    @APIResponse(responseCode = "409", description = "O nível está em uso por um ou mais membros ativos, ou referenciado por convites pendentes.")
    public Response deleteAccessLevel(
        @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
        @PathParam("tenantId") UUID tenantId,
        @Parameter(description = "Identificador do nível de acesso a ser excluído.", required = true)
        @PathParam("alId") UUID alId,
        @Context SecurityContext ctx
    ) {
        TenantContext tc = TenantContext.resolveAndCheck(ctx, tenantId);
        adminAccessService.requireAdminPerm(tc, tenantId, "access_levels.delete");
        accessLevelService.deleteAccessLevel(tenantId, alId);
        return Response.noContent().build();
    }
}
