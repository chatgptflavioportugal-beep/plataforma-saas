package com.saas.profile.controller;

import com.saas.profile.security.TenantContext;
import com.saas.profile.service.InvitationService;
import com.saas.profile.service.TenantService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Perfil do tenant, membros e convites de uma empresa/perfil.
 *
 * Todas as rotas exigem Supabase JWT + X-Tenant-ID resolvido pelo
 * TenantResolutionFilter (o {tenantId} do path precisa coincidir com o tenant
 * resolvido). As rotas de membros/convites exigem, além disso, a permissão
 * administrativa correspondente do usuário no tenant (owner/admin possuem
 * todas; membros dependem das permissões do seu nível de acesso).
 */
@Path("/api/v1/tenants/{tenantId}")
@Tag(name = "Members", description = "Perfil do tenant, gestão de membros e convites de uma empresa/perfil.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InvitationResource {

    @Inject
    InvitationService invitationService;

    @Inject
    TenantService tenantService;

    @Inject
    EntityManager em;

    // ─── Perfil do tenant ────────────────────────────────────────────────────

    @GET
    @Path("/profile")
    @Operation(
        summary = "Consulta o perfil completo do tenant: dados, assinatura e plano",
        description = "Operação exclusivamente de consulta. Retorna os dados cadastrais do " +
            "tenant, a assinatura atual (`tenant_subscriptions`, com status, período e tipo de " +
            "cobrança) e o plano contratado (com preços, limites e módulos incluídos), além do " +
            "papel do usuário chamador no tenant. Se o tenant ainda não possuir nenhuma " +
            "assinatura (caso de tenants individuais criados por trigger antes de haver " +
            "planos), uma assinatura é criada automaticamente com o plano disponível de menor " +
            "ordem antes de retornar a resposta."
    )
    @APIResponse(responseCode = "200", description = "Perfil do tenant, assinatura e plano.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID.")
    @APIResponse(responseCode = "404", description = "Tenant não encontrado.")
    public Response tenantProfile(
            @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
            @PathParam("tenantId") UUID tenantId,
            @Context SecurityContext ctx) {
        TenantContext tenantCtx = TenantContext.from(ctx);
        if (!tenantCtx.getTenantId().equals(tenantId)) {
            return Response.status(403).build();
        }
        return Response.ok(tenantService.getTenantProfile(tenantId)).build();
    }

    // ─── Membros ──────────────────────────────────────────────────────────────

    @GET
    @Path("/members")
    @Operation(
        summary = "Lista os membros ativos do tenant",
        description = "Operação exclusivamente de consulta. Retorna todos os vínculos " +
            "usuário-tenant ativos (`user_tenants.is_active = TRUE`), com nome, e-mail, papel " +
            "(owner/admin/member) e, quando aplicável, o nível de acesso atribuído. Requer a " +
            "permissão administrativa `members.view`."
    )
    @APIResponse(responseCode = "200", description = "Lista de membros ativos do tenant.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID, ou o usuário não possui a permissão `members.view`.")
    public Response listMembers(
            @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
            @PathParam("tenantId") UUID tenantId,
            @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "members.view");
        return Response.ok(invitationService.listMembers(tenantId)).build();
    }

    @DELETE
    @Path("/members/{userId}")
    @Operation(
        summary = "Remove um membro do tenant",
        description = "Faz soft delete do vínculo usuário-tenant (`is_active = FALSE`), " +
            "revogando o acesso do membro ao perfil. Regras de negócio aplicadas pelo " +
            "InvitationService: (1) ninguém pode remover a si mesmo; (2) o proprietário " +
            "(`owner`) do tenant nunca pode ser removido por este endpoint; (3) um usuário " +
            "com papel `admin` não pode remover outro `admin` (apenas o `owner` pode). Ao " +
            "final, incrementa a versão de permissões do membro removido, invalidando " +
            "qualquer ProfileAccessToken/ModuleAccessToken em cache dele. Requer a permissão " +
            "administrativa `members.remove`."
    )
    @APIResponse(responseCode = "204", description = "Membro removido com sucesso.")
    @APIResponse(responseCode = "400", description = "O usuário autenticado tentou remover a si mesmo.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID, o usuário não possui a permissão `members.remove`, o alvo é o proprietário do tenant, ou um `admin` tentou remover outro `admin`.")
    @APIResponse(responseCode = "404", description = "Nenhum membro ativo com o `userId` informado foi encontrado neste tenant.")
    public Response removeMember(
            @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
            @PathParam("tenantId") UUID tenantId,
            @Parameter(description = "Identificador do usuário a ser removido do tenant.", required = true)
            @PathParam("userId") UUID targetUserId,
            @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "members.remove");
        invitationService.removeMember(tenantId, targetUserId, tc.getUserId(), tc.getUserRole());
        return Response.noContent().build();
    }

    @PATCH
    @Path("/members/{userId}/access-level")
    @Operation(
        summary = "Altera o nível de acesso de um membro",
        description = "Atualiza o `access_level_id` do vínculo usuário-tenant para o " +
            "`accessLevelId` informado no corpo. O nível de acesso precisa existir, pertencer " +
            "a este tenant e estar com status ACTIVE. Aplicável apenas a membros com papel " +
            "`member` — owners e admins não possuem nível de acesso atribuído. Ao final, " +
            "incrementa a versão de permissões do membro, invalidando qualquer " +
            "ProfileAccessToken/ModuleAccessToken em cache dele. Requer a permissão " +
            "administrativa `members.change_access_level`."
    )
    @APIResponse(responseCode = "200", description = "Nível de acesso alterado com sucesso.")
    @APIResponse(responseCode = "400", description = "`accessLevelId` ausente/em branco no corpo, formato de UUID inválido, nível de acesso inválido/inativo, ou o alvo não possui papel `member`.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID, ou o usuário não possui a permissão `members.change_access_level`.")
    @APIResponse(responseCode = "404", description = "Nenhum membro ativo com o `userId` informado foi encontrado neste tenant.")
    public Response changeMemberAccessLevel(
            @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
            @PathParam("tenantId") UUID tenantId,
            @Parameter(description = "Identificador do membro cujo nível de acesso será alterado.", required = true)
            @PathParam("userId") UUID targetUserId,
            Map<String, String> body,
            @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "members.change_access_level");

        String accessLevelId = body != null ? body.get("accessLevelId") : null;
        if (accessLevelId == null || accessLevelId.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Nível de acesso é obrigatório")).build();
        }

        invitationService.changeMemberAccessLevel(tenantId, targetUserId, accessLevelId);
        return Response.ok(Map.of("success", true)).build();
    }

    // ─── Convites ─────────────────────────────────────────────────────────────

    @GET
    @Path("/invitations")
    @Operation(
        summary = "Lista o histórico de convites do tenant",
        description = "Operação exclusivamente de consulta. Retorna todos os convites do " +
            "tenant (pendentes, aceitos, cancelados e expirados), ordenados do mais recente " +
            "para o mais antigo, com e-mail, papel, status, nível de acesso e data de " +
            "expiração de cada um. Requer a permissão administrativa `invites.view`."
    )
    @APIResponse(responseCode = "200", description = "Lista de convites do tenant.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID, ou o usuário não possui a permissão `invites.view`.")
    public Response listInvitations(
            @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
            @PathParam("tenantId") UUID tenantId,
            @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "invites.view");
        return Response.ok(invitationService.listInvitations(tenantId)).build();
    }

    @POST
    @Path("/invitations")
    @Operation(
        summary = "Envia um convite de membro por e-mail",
        description = "Cria um convite pendente (validade padrão da tabela `invitations`) " +
            "para o `email` informado, vinculado ao `accessLevelId` (nível de acesso ativo do " +
            "tenant) informado, e dispara um e-mail com o link de aceite " +
            "(`{app.base-url}/invite/accept?token=...`). Regras aplicadas pelo " +
            "InvitationService: o e-mail é normalizado (trim + lowercase); o nível de acesso " +
            "precisa existir, pertencer ao tenant e estar ACTIVE; o e-mail não pode já " +
            "pertencer a um membro ativo do tenant; e não pode haver outro convite `pending` " +
            "e não expirado para o mesmo e-mail neste tenant. Requer a permissão " +
            "administrativa `members.invite`."
    )
    @APIResponse(responseCode = "200", description = "Convite criado e e-mail disparado; retorna id, e-mail normalizado, nível de acesso e data de expiração do convite.")
    @APIResponse(responseCode = "400", description = "`email` ou `accessLevelId` ausente/em branco, nível de acesso inválido/inativo, e-mail já é membro ativo do tenant, ou já existe convite pendente para este e-mail.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID, ou o usuário não possui a permissão `members.invite`.")
    public Response sendInvitation(
            @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
            @PathParam("tenantId") UUID tenantId,
            Map<String, String> body,
            @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "members.invite");

        String email = body.get("email");
        String accessLevelId = body.get("accessLevelId");

        if (email == null || email.isBlank()) {
            return Response.status(400).entity(Map.of("error", "E-mail é obrigatório")).build();
        }
        if (accessLevelId == null || accessLevelId.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Nível de acesso é obrigatório")).build();
        }

        var result = invitationService.sendInvitation(tenantId, email, accessLevelId, tc.getUserId());
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/invitations/{invId}")
    @Operation(
        summary = "Cancela um convite pendente",
        description = "Altera o status do convite para `cancelled`. Só afeta convites que " +
            "ainda estejam com status `pending` — convites já aceitos, cancelados ou expirados " +
            "não são alterados e a requisição é tratada como \"não encontrado\". Requer a " +
            "permissão administrativa `invites.cancel`."
    )
    @APIResponse(responseCode = "204", description = "Convite cancelado com sucesso.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente/inválido/expirado, ou X-Tenant-ID ausente/não resolvido para o usuário (TenantResolutionFilter).")
    @APIResponse(responseCode = "403", description = "O `tenantId` do path não corresponde ao tenant resolvido pelo X-Tenant-ID, ou o usuário não possui a permissão `invites.cancel`.")
    @APIResponse(responseCode = "404", description = "Nenhum convite com o `invId` informado existe neste tenant com status `pending` (inclui o caso de já ter sido aceito/cancelado/expirado).")
    public Response cancelInvitation(
            @Parameter(description = "Identificador do tenant (deve coincidir com o tenant resolvido via X-Tenant-ID).", required = true)
            @PathParam("tenantId") UUID tenantId,
            @Parameter(description = "Identificador do convite a ser cancelado.", required = true)
            @PathParam("invId") UUID invId,
            @Context SecurityContext ctx) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "invites.cancel");
        invitationService.cancelInvitation(tenantId, invId);
        return Response.noContent().build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean hasAdminPerm(TenantContext tc, UUID tenantId, String permKey) {
        if (List.of("owner", "admin").contains(tc.getUserRole())) return true;
        long count = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM profile_access_level_admin_permissions ap " +
            "JOIN user_tenants ut ON ut.access_level_id = ap.access_level_id " +
            "WHERE ut.user_id = :userId AND ut.tenant_id = :tenantId " +
            "  AND ut.is_active = TRUE AND ap.permission_key = :permKey"
        )
        .setParameter("userId", tc.getUserId())
        .setParameter("tenantId", tenantId)
        .setParameter("permKey", permKey)
        .getSingleResult()).longValue();
        return count > 0;
    }

    private void requireAdminPerm(TenantContext tc, UUID tenantId, String permKey) {
        if (!hasAdminPerm(tc, tenantId, permKey)) {
            throw new ForbiddenException("Permissão necessária: " + permKey);
        }
    }

    private TenantContext resolveAndCheckAccess(SecurityContext ctx, UUID tenantId) {
        TenantContext tc = TenantContext.from(ctx);
        if (!tc.getTenantId().equals(tenantId)) {
            throw new ForbiddenException("Acesso negado ao tenant");
        }
        return tc;
    }
}
