package com.saas.admin.controller;

import com.saas.admin.dto.FeatureFlagDTO;
import com.saas.admin.dto.FeatureFlagRequest;
import com.saas.admin.security.AdminAuthService;
import com.saas.admin.service.FeatureFlagService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cadastro de Feature Flags da plataforma — admin-service é o único dono
 * (tabela feature_flags); nenhum outro serviço escreve aqui.
 */
@Path("/api/v1/admin/feature-flags")
@Tag(name = "Configuration", description = "Cadastro de Feature Flags da plataforma. O admin-service é o único dono da " +
    "tabela feature_flags — nenhum outro serviço escreve nela. Todas as rotas deste controller pertencem " +
    "exclusivamente ao contexto administrativo e não devem ser utilizadas pelo ambiente cliente.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminFeatureFlagResource {

    @Inject AdminAuthService adminAuth;
    @Inject FeatureFlagService featureFlagService;

    @GET
    @Operation(
        summary = "Lista todas as feature flags cadastradas",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Lista todos os registros da tabela feature_flags, ordenados por 'key', sem nenhum " +
            "filtro. Requer a permissão granular 'admin.feature_flags.view'."
    )
    @APIResponse(responseCode = "200", description = "Lista de feature flags cadastradas.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.feature_flags.view'.")
    public Response list() {
        adminAuth.requireAdminPermission("admin.feature_flags.view");

        List<FeatureFlagDTO> flags = featureFlagService.list();
        return Response.ok(flags).build();
    }

    @POST
    @Operation(
        summary = "Cria uma nova feature flag",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Cria um registro em feature_flags a partir de 'key' e 'name' (ambos obrigatórios), " +
            "'description' opcional e 'isEnabled' (padrão false quando ausente). A 'key' deve ser única — a " +
            "criação é recusada se já existir uma flag com a mesma chave. O usuário autenticado é gravado " +
            "como updated_by_user_id. Requer a permissão granular 'admin.feature_flags.create'."
    )
    @APIResponse(responseCode = "201", description = "Feature flag criada com sucesso; retorna o id gerado.")
    @APIResponse(responseCode = "400", description = "'key' ou 'name' ausentes/em branco, ou já existe uma feature flag com a mesma 'key'.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.feature_flags.create'.")
    public Response create(FeatureFlagRequest req) {
        adminAuth.requireAdminPermission("admin.feature_flags.create");

        if (req == null || req.key() == null || req.key().isBlank())
            return Response.status(400).entity(Map.of("error", "key é obrigatório")).build();
        if (req.name() == null || req.name().isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();

        if (featureFlagService.existsByKey(req.key()))
            return Response.status(400).entity(Map.of("error", "Já existe uma feature flag com esta key")).build();

        UUID id = featureFlagService.create(req, adminAuth.currentUserId());

        return Response.status(201).entity(Map.of("id", id.toString(), "created", true)).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(
        summary = "Edita nome e descrição de uma feature flag",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Atualiza 'name' (obrigatório) e 'description' da feature flag identificada por 'id'. " +
            "Não altera 'key' nem 'isEnabled' (o toggle de ativação é feito pela rota separada " +
            "PATCH /{id}/status). O usuário autenticado é gravado como updated_by_user_id. Requer a permissão " +
            "granular 'admin.feature_flags.edit'."
    )
    @APIResponse(responseCode = "200", description = "Feature flag atualizada com sucesso.")
    @APIResponse(responseCode = "400", description = "'name' ausente ou em branco.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.feature_flags.edit'.")
    @APIResponse(responseCode = "404", description = "Nenhuma feature flag encontrada para o 'id' informado.")
    public Response update(
            @Parameter(description = "Identificador (UUID) da feature flag.", required = true)
            @PathParam("id") String id,
            FeatureFlagRequest req) {
        adminAuth.requireAdminPermission("admin.feature_flags.edit");

        if (req == null || req.name() == null || req.name().isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();

        boolean updated = featureFlagService.update(id, req, adminAuth.currentUserId());
        if (!updated) return Response.status(404).entity(Map.of("error", "Feature flag não encontrada")).build();

        return Response.ok(Map.of("id", id, "updated", true)).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Consumes(MediaType.WILDCARD)
    @Operation(
        summary = "Alterna (toggle) o estado ativo/inativo de uma feature flag",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Inverte o valor atual de is_enabled (não recebe corpo — aceita qualquer Content-Type via " +
            "@Consumes(WILDCARD)) da feature flag identificada por 'id'. O usuário autenticado é gravado como " +
            "updated_by_user_id. Requer a permissão granular 'admin.feature_flags.activate'."
    )
    @APIResponse(responseCode = "200", description = "Feature flag alternada com sucesso.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.feature_flags.activate'.")
    @APIResponse(responseCode = "404", description = "Nenhuma feature flag encontrada para o 'id' informado.")
    public Response toggleStatus(
            @Parameter(description = "Identificador (UUID) da feature flag.", required = true)
            @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.feature_flags.activate");

        boolean toggled = featureFlagService.toggleStatus(id, adminAuth.currentUserId());
        if (!toggled) return Response.status(404).entity(Map.of("error", "Feature flag não encontrada")).build();

        return Response.ok(Map.of("id", id, "toggled", true)).build();
    }
}
