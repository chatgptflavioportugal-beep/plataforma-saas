package com.saas.admin.controller;

import com.saas.admin.security.AdminAuthService;
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
import java.util.stream.Collectors;

/**
 * Configurações Gerais da Plataforma — tabela chave/valor simples. Movido de
 * subscription-service para admin-service (Configurações Globais). Hoje só
 * expõe o cooldown de reutilização de Trial, mas é genérica para futuras
 * configurações.
 */
@Path("/api/v1/admin/platform-settings")
@Tag(name = "Configuration", description = "Configurações gerais da plataforma, armazenadas como pares chave/valor (platform_settings) — hoje limitado ao cooldown de reutilização de Trial, mas genérico para futuras configurações globais. Contexto exclusivamente administrativo — não deve ser utilizado pelo ambiente cliente.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminPlatformSettingsResource {

    @Inject EntityManager em;
    @Inject AdminAuthService adminAuth;

    public record UpdateSettingRequest(String value) {}

    @GET
    @Operation(
        summary = "Lista todas as configurações gerais da plataforma",
        description = "Operação exclusivamente administrativa e de consulta — não deve ser " +
            "utilizada pelo ambiente cliente. Retorna todos os pares chave/valor de " +
            "platform_settings (`key`, `value`, `description`, `updatedAt`), ordenados por " +
            "`key`."
    )
    @APIResponse(responseCode = "200", description = "Lista de configurações da plataforma.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.settings.view`.")
    @SuppressWarnings("unchecked")
    public Response list() {
        adminAuth.requireAdminPermission("admin.settings.view");

        List<Object[]> rows = em.createNativeQuery(
            "SELECT key, value, description, updated_at::text FROM platform_settings ORDER BY key"
        ).getResultList();

        return Response.ok(rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", row[0]);
            m.put("value", row[1]);
            m.put("description", row[2]);
            m.put("updatedAt", row[3]);
            return m;
        }).collect(Collectors.toList())).build();
    }

    @PUT
    @Path("/{key}")
    @Transactional
    @Operation(
        summary = "Atualiza o valor de uma configuração geral da plataforma",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Atualiza o `value` (obrigatório, não pode ser vazio) da " +
            "configuração identificada por `key`. Para a chave especial " +
            "`trial_reuse_cooldown_days`, o `value` é validado adicionalmente como número " +
            "inteiro não-negativo antes de ser gravado. Registra o administrador responsável " +
            "(`updated_by_user_id`) e o timestamp da alteração."
    )
    @APIResponse(responseCode = "200", description = "Configuração atualizada com sucesso; retorna `key`, `value` e `updated = true`.")
    @APIResponse(responseCode = "400", description = "`value` ausente ou em branco, ou (para `trial_reuse_cooldown_days`) `value` não é um número inteiro ou é negativo.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.settings.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhuma configuração encontrada para a `key` informada.")
    public Response update(
            @Parameter(description = "Chave (platform_settings.key) da configuração a atualizar.", required = true) @PathParam("key") String key,
            UpdateSettingRequest req) {
        adminAuth.requireAdminPermission("admin.settings.edit");

        if (req == null || req.value() == null || req.value().isBlank())
            throw new BadRequestException("value é obrigatório");

        if ("trial_reuse_cooldown_days".equals(key)) {
            try {
                int days = Integer.parseInt(req.value().trim());
                if (days < 0) throw new BadRequestException("trial_reuse_cooldown_days não pode ser negativo");
            } catch (NumberFormatException e) {
                throw new BadRequestException("trial_reuse_cooldown_days deve ser um número inteiro");
            }
        }

        int updated = em.createNativeQuery(
            "UPDATE platform_settings SET value = :value, updated_at = NOW(), " +
            "updated_by_user_id = CAST(:userId AS uuid) WHERE key = :key"
        )
            .setParameter("value", req.value().trim())
            .setParameter("userId", adminAuth.currentUserId())
            .setParameter("key", key)
            .executeUpdate();

        if (updated == 0) throw new NotFoundException("Configuração não encontrada: " + key);
        return Response.ok(Map.of("key", key, "value", req.value().trim(), "updated", true)).build();
    }
}
