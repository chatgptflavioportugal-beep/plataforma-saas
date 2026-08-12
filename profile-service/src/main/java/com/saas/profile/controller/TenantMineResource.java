package com.saas.profile.controller;

import com.saas.profile.repository.UserTenantRepository;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

/**
 * Path explicitamente excluído da resolução de tenant pelo TenantResolutionFilter
 * (path.equals("/api/v1/tenants/mine")) — não exige X-Tenant-ID, apenas o JWT do
 * Supabase, já que lista justamente os tenants aos quais o usuário pode se conectar.
 */
@Path("/api/v1/tenants/mine")
@Tag(name = "Companies", description = "Consulta dos tenants/perfis aos quais o usuário autenticado está vinculado.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
public class TenantMineResource {

    @Inject
    UserTenantRepository userTenantRepository;

    @Inject
    JsonWebToken jwt;

    @GET
    @Operation(
        summary = "Lista todos os tenants/perfis do usuário autenticado",
        description = "Operação exclusivamente de consulta, usada pela tela de seleção de " +
            "perfil após o login. Retorna, para cada vínculo usuário-tenant do usuário " +
            "autenticado (ativos e inativos), o papel (owner/admin/member/individual) e os " +
            "dados do tenant associado (nome, slug, status, tipo, plano, data de fim do " +
            "trial). Não exige o header X-Tenant-ID — este é o próprio endpoint usado antes " +
            "de haver um tenant ativo selecionado."
    )
    @APIResponse(responseCode = "200", description = "Lista de vínculos usuário-tenant do usuário autenticado, cada um com os dados do respectivo tenant.")
    @APIResponse(responseCode = "401", description = "JWT do Supabase ausente, inválido ou expirado.")
    public Response myTenants() {
        UUID userId = UUID.fromString(jwt.getSubject());
        var rows = userTenantRepository.findAllByUser(userId);
        var result = rows.stream().map(r -> {
            var tenant = new java.util.LinkedHashMap<String, Object>();
            tenant.put("id", r.tenantId());
            tenant.put("name", r.tenantName());
            tenant.put("slug", r.tenantSlug());
            tenant.put("status", r.tenantStatus());
            tenant.put("type", r.tenantType());
            tenant.put("plan_id", r.planId());
            tenant.put("trial_ends_at", r.trialEndsAt());
            tenant.put("created_at", r.createdAt());
            tenant.put("updated_at", r.updatedAt());

            var ut = new java.util.LinkedHashMap<String, Object>();
            ut.put("id", r.id());
            ut.put("user_id", r.userId());
            ut.put("tenant_id", r.tenantId());
            ut.put("role", r.role());
            ut.put("is_active", r.isActive());
            ut.put("tenant", tenant);
            return ut;
        }).toList();
        return Response.ok(result).build();
    }
}
