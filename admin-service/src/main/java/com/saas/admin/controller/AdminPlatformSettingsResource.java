package com.saas.admin.controller;

import com.saas.admin.security.AdminAuthService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
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
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminPlatformSettingsResource {

    @Inject EntityManager em;
    @Inject AdminAuthService adminAuth;

    public record UpdateSettingRequest(String value) {}

    @GET
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
    public Response update(@PathParam("key") String key, UpdateSettingRequest req) {
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
