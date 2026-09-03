package com.saas.admin.resource;

import com.saas.admin.dto.PlatformSettingDTO;
import com.saas.admin.dto.UpdateSettingRequest;
import com.saas.platformadmin.PlatformAdminAuthService;
import com.saas.admin.negocio.impl.PlatformSettingsNegocio;
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

    @Inject PlatformAdminAuthService adminAuth;
    @Inject PlatformSettingsNegocio settingsNegocio;

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
    public Response list() {
        adminAuth.requireAdminPermission("admin.settings.view");

        List<PlatformSettingDTO> settings = settingsNegocio.list();
        return Response.ok(settings).build();
    }

    @PUT
    @Path("/{key}")
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

        if (req == null)
            throw new BadRequestException("value é obrigatório");

        String value = settingsNegocio.updateValue(key, req.value(), adminAuth.currentUserId());
        return Response.ok(Map.of("key", key, "value", value, "updated", true)).build();
    }
}
