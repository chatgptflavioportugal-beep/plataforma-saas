package com.saas.admin.controller;

import com.saas.admin.security.AdminAuthService;
import com.saas.admin.service.AdminAuditService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Inject EntityManager em;
    @Inject AdminAuthService adminAuth;
    @Inject AdminAuditService auditService;

    public record FeatureFlagRequest(String key, String name, String description, Boolean isEnabled) {}

    @GET
    @SuppressWarnings("unchecked")
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

        List<Object[]> rows = em.createNativeQuery(
            "SELECT id::text, key, name, description, is_enabled, created_at::text, updated_at::text " +
            "FROM feature_flags ORDER BY key"
        ).getResultList();

        return Response.ok(rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("key", row[1]);
            m.put("name", row[2]);
            m.put("description", row[3]);
            m.put("is_enabled", row[4]);
            m.put("created_at", row[5]);
            m.put("updated_at", row[6]);
            return m;
        }).collect(Collectors.toList())).build();
    }

    @POST
    @Transactional
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

        long existing = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM feature_flags WHERE key = :key"
        ).setParameter("key", req.key()).getSingleResult()).longValue();
        if (existing > 0)
            return Response.status(400).entity(Map.of("error", "Já existe uma feature flag com esta key")).build();

        UUID id = UUID.randomUUID();
        em.createNativeQuery(
            "INSERT INTO feature_flags (id, key, name, description, is_enabled, updated_by_user_id) " +
            "VALUES (:id, :key, :name, :description, :isEnabled, CAST(:userId AS uuid))"
        )
            .setParameter("id", id)
            .setParameter("key", req.key())
            .setParameter("name", req.name())
            .setParameter("description", req.description())
            .setParameter("isEnabled", Boolean.TRUE.equals(req.isEnabled()))
            .setParameter("userId", adminAuth.currentUserId())
            .executeUpdate();

        auditService.log(adminAuth.currentUserId(), "feature_flag.created", "feature_flags", id.toString(),
            Map.of("key", req.key(), "isEnabled", Boolean.TRUE.equals(req.isEnabled())));

        return Response.status(201).entity(Map.of("id", id.toString(), "created", true)).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
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

        int updated = em.createNativeQuery(
            "UPDATE feature_flags SET name = :name, description = :description, updated_at = NOW(), " +
            "updated_by_user_id = CAST(:userId AS uuid) WHERE id::text = :id"
        )
            .setParameter("name", req.name())
            .setParameter("description", req.description())
            .setParameter("userId", adminAuth.currentUserId())
            .setParameter("id", id)
            .executeUpdate();

        if (updated == 0) return Response.status(404).entity(Map.of("error", "Feature flag não encontrada")).build();

        auditService.log(adminAuth.currentUserId(), "feature_flag.updated", "feature_flags", id, Map.of());
        return Response.ok(Map.of("id", id, "updated", true)).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Consumes(MediaType.WILDCARD)
    @Transactional
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

        int updated = em.createNativeQuery(
            "UPDATE feature_flags SET is_enabled = NOT is_enabled, updated_at = NOW(), " +
            "updated_by_user_id = CAST(:userId AS uuid) WHERE id::text = :id"
        ).setParameter("userId", adminAuth.currentUserId()).setParameter("id", id).executeUpdate();

        if (updated == 0) return Response.status(404).entity(Map.of("error", "Feature flag não encontrada")).build();

        auditService.log(adminAuth.currentUserId(), "feature_flag.toggled", "feature_flags", id, Map.of());
        return Response.ok(Map.of("id", id, "toggled", true)).build();
    }
}
