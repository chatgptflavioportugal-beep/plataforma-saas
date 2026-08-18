package com.saas.profile.controller;

import com.saas.profile.dto.request.OnboardingRequest;
import com.saas.profile.dto.tenant.OnboardingResponse;
import com.saas.profile.entity.Tenant;
import com.saas.profile.service.AdminAccessService;
import com.saas.profile.service.InvitationService;
import com.saas.profile.service.TenantService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoints públicos do domínio de perfil: onboarding (criação de tenant),
 * criação do tenant individual, e preview público de convite.
 *
 * A listagem de planos/módulos de billing (/api/v1/public/plans,
 * /api/v1/public/modules/billing-options) já migrou para o subscription-service.
 */
@Path("/api/v1/public")
@Tag(name = "Public", description = "Onboarding de tenants, criação do tenant individual e preview público de convites. Nem todas as rotas são de fato anônimas — ver descrição de cada operação.")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublicResource {

    @Inject
    TenantService tenantService;

    @Inject
    InvitationService invitationService;

    @Inject
    AdminAccessService adminAccessService;

    @Inject
    JsonWebToken jwt;

    /**
     * Onboarding: cria um tenant do tipo BUSINESS.
     * Requer usuário autenticado — não exige tenant ativo (sem X-Tenant-ID).
     */
    @POST
    @Path("/onboarding")
    @Authenticated
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Cria o tenant de empresa (onboarding) do usuário autenticado",
        description = "Apesar de estar sob `/api/v1/public`, requer autenticação: exige o JWT " +
            "do Supabase, mas não o header X-Tenant-ID (o path é explicitamente excluído da " +
            "resolução de tenant do TenantResolutionFilter, já que o usuário ainda não possui " +
            "tenant). Cria um novo tenant com `type=business`, associa o plano gratuito do " +
            "tipo `business` (ou, na ausência dele, o primeiro plano ativo disponível), cria o " +
            "vínculo do usuário como `owner` e uma assinatura `active`. Usuários administrativos " +
            "(system_role SUPER_ADMIN/ADMIN_USER) não podem criar perfis de cliente."
    )
    @APIResponse(responseCode = "200", description = "Tenant criado com sucesso; retorna `id`, `slug` e `type=business`.")
    @APIResponse(responseCode = "400", description = "`name` ou `slug` ausente no corpo da requisição.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "O usuário autenticado é administrativo (SUPER_ADMIN/ADMIN_USER) e não pode criar perfis de cliente.")
    @APIResponse(responseCode = "409", description = "O `slug` informado já está em uso por outro tenant.")
    public Response onboarding(OnboardingRequest body) {
        UUID userId = UUID.fromString(jwt.getSubject());
        if (adminAccessService.isPlatformAdmin(userId))
            return Response.status(403).entity(Map.of("error", "Usuários administrativos não podem criar perfis cliente")).build();

        String name = body != null ? body.name() : null;
        String slug = body != null ? body.slug() : null;

        if (name == null || slug == null) {
            return Response.status(400).entity(Map.of("error", "name e slug obrigatórios")).build();
        }

        try {
            Tenant tenant = tenantService.createTenant(name, slug, userId, "business");
            return Response.ok(new OnboardingResponse(tenant.id, tenant.slug, "business")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(409).entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * Cria (ou retorna) o tenant individual do usuário autenticado.
     * Idempotente — seguro chamar múltiplas vezes.
     * Requer usuário autenticado — não exige tenant ativo (sem X-Tenant-ID).
     */
    @POST
    @Path("/individual-tenant")
    @Authenticated
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Garante a existência do tenant individual do usuário autenticado",
        description = "Apesar de estar sob `/api/v1/public`, requer autenticação: exige o JWT " +
            "do Supabase, mas não o header X-Tenant-ID (path excluído da resolução de tenant " +
            "do TenantResolutionFilter). Idempotente — usado como backfill: se o usuário já " +
            "possui um tenant com `type=individual`, apenas o retorna (`already_exists=true`); " +
            "caso contrário, cria um novo tenant individual (nome derivado do perfil do " +
            "usuário, ou \"Meu Plano\" como fallback), com o vínculo `owner` e assinatura " +
            "própria. Usuários administrativos (system_role SUPER_ADMIN/ADMIN_USER) não podem " +
            "criar perfis de cliente."
    )
    @APIResponse(responseCode = "200", description = "Tenant individual existente retornado, ou criado agora; distinguível pelo campo `already_exists`.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "O usuário autenticado é administrativo (SUPER_ADMIN/ADMIN_USER) e não pode criar perfis de cliente.")
    @APIResponse(responseCode = "500", description = "Falha inesperada ao criar o tenant individual; a mensagem da exceção é incluída na resposta.")
    public Response createIndividualTenant() {
        UUID userId = UUID.fromString(jwt.getSubject());
        if (adminAccessService.isPlatformAdmin(userId))
            return Response.status(403).entity(Map.of("error", "Usuários administrativos não podem criar perfis cliente")).build();

        try {
            var result = tenantService.ensureIndividualTenant(userId);
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * Preview público de um convite (sem autenticação).
     * Mostra nome da empresa, papel e status do convite.
     */
    @GET
    @Path("/invitations/{token}")
    @Operation(
        summary = "Consulta o preview público de um convite pelo token",
        description = "Endpoint verdadeiramente público — sem `@Authenticated`, não exige " +
            "nenhum token. Usado pela tela de aceite de convite para exibir, antes do login, " +
            "o e-mail convidado, o nome da empresa, o papel e o nível de acesso oferecidos, e " +
            "o status atual do convite (pending/accepted/cancelled) e sua data de expiração — " +
            "sem revelar nenhum outro dado do tenant."
    )
    @APIResponse(responseCode = "200", description = "Dados de preview do convite.")
    @APIResponse(responseCode = "404", description = "Nenhum convite corresponde ao `token` informado.")
    public Response previewInvitation(
            @Parameter(description = "Token único do convite.", required = true)
            @PathParam("token") String token) {
        try {
            return Response.ok(invitationService.getInvitationPreview(token)).build();
        } catch (NotFoundException e) {
            return Response.status(404).entity(Map.of("error", "Convite não encontrado")).build();
        }
    }
}
