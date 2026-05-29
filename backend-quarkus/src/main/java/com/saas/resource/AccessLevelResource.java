package com.saas.resource;

import com.saas.security.TenantContext;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.*;
import java.util.stream.Collectors;

@Path("/api/v1/tenants/{tenantId}/access-levels")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccessLevelResource {

    @Inject
    EntityManager em;


    // ─── GET /available-modules — módulos+serviços com assinatura ativa ────────

    @GET
    @Path("/available-modules")
    @SuppressWarnings("unchecked")
    public Response availableModules(
        @PathParam("tenantId") UUID tenantId,
        @Context SecurityContext ctx
    ) {
        resolveAndCheckAccess(ctx, tenantId);

        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "SELECT pm.id::text, pm.name, pm.slug, pm.icon_path, " +
            "       pms.id::text, pms.name, pms.slug, pms.icon_path " +
            "FROM profile_module_subscriptions sub " +
            "JOIN platform_modules pm ON pm.id = sub.module_id " +
            "JOIN platform_module_services pms ON pms.module_id = pm.id " +
            "WHERE sub.tenant_id = :tenantId AND sub.status = 'ACTIVE' " +
            "  AND pm.is_active = TRUE AND pms.is_active = TRUE " +
            "ORDER BY pm.sort_order, pms.sort_order"
        ).setParameter("tenantId", tenantId).getResultList();

        Map<String, Map<String, Object>> modules = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String moduleId = (String) row[0];
            if (!modules.containsKey(moduleId)) {
                Map<String, Object> mod = new LinkedHashMap<>();
                mod.put("moduleId", moduleId);
                mod.put("moduleName", row[1]);
                mod.put("moduleSlug", row[2]);
                mod.put("moduleIconPath", row[3]);
                mod.put("services", new ArrayList<Map<String, Object>>());
                modules.put(moduleId, mod);
            }
            Map<String, Object> svc = new LinkedHashMap<>();
            svc.put("serviceId", row[4]);
            svc.put("serviceName", row[5]);
            svc.put("serviceSlug", row[6]);
            svc.put("serviceIconPath", row[7]);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> services = (List<Map<String, Object>>) modules.get(moduleId).get("services");
            services.add(svc);
        }

        return Response.ok(new ArrayList<>(modules.values())).build();
    }

    // ─── GET / — listar níveis de acesso ─────────────────────────────────────

    @GET
    @SuppressWarnings("unchecked")
    public Response listAccessLevels(
        @PathParam("tenantId") UUID tenantId,
        @Context SecurityContext ctx
    ) {
        resolveAndCheckAccess(ctx, tenantId);

        List<Object[]> levels = (List<Object[]>) em.createNativeQuery(
            "SELECT id::text, name, description, status, created_at::text, updated_at::text " +
            "FROM profile_access_levels " +
            "WHERE tenant_id = :tenantId " +
            "ORDER BY created_at ASC"
        ).setParameter("tenantId", tenantId).getResultList();

        List<Map<String, Object>> result = levels.stream().map(row -> {
            String levelId = (String) row[0];

            List<Object[]> perms = (List<Object[]>) em.createNativeQuery(
                "SELECT id::text, module_id::text, service_id::text " +
                "FROM profile_access_level_permissions " +
                "WHERE access_level_id = :levelId"
            ).setParameter("levelId", UUID.fromString(levelId)).getResultList();

            List<Map<String, Object>> permissions = perms.stream().map(prow -> {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("id", prow[0]);
                pm.put("moduleId", prow[1]);
                pm.put("serviceId", prow[2]);
                return pm;
            }).collect(Collectors.toList());

            long memberCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM user_tenants WHERE access_level_id = :levelId AND is_active = TRUE"
            ).setParameter("levelId", UUID.fromString(levelId)).getSingleResult()).longValue();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", levelId);
            m.put("name", row[1]);
            m.put("description", row[2]);
            m.put("status", row[3]);
            m.put("createdAt", row[4]);
            m.put("updatedAt", row[5]);
            m.put("permissions", permissions);
            m.put("memberCount", memberCount);
            return m;
        }).collect(Collectors.toList());

        return Response.ok(result).build();
    }

    // ─── POST / — criar nível de acesso ──────────────────────────────────────

    @POST
    @Transactional
    @SuppressWarnings("unchecked")
    public Response createAccessLevel(
        @PathParam("tenantId") UUID tenantId,
        Map<String, Object> body,
        @Context SecurityContext ctx
    ) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        ensureOwnerOrAdmin(tc);

        String name = body != null ? (String) body.get("name") : null;
        if (name == null || name.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Nome é obrigatório")).build();
        }

        Object rawDesc = body.get("description");
        String description = rawDesc != null ? rawDesc.toString().trim() : null;
        List<String> serviceIds = (List<String>) body.get("serviceIds");

        em.createNativeQuery(
            "INSERT INTO profile_access_levels (tenant_id, name, description) " +
            "VALUES (:tenantId, :name, :description)"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("name", name.trim())
        .setParameter("description", description)
        .executeUpdate();

        String levelId = (String) em.createNativeQuery(
            "SELECT id::text FROM profile_access_levels " +
            "WHERE tenant_id = :tenantId ORDER BY created_at DESC LIMIT 1"
        ).setParameter("tenantId", tenantId).getSingleResult();

        if (serviceIds != null && !serviceIds.isEmpty()) {
            try {
                insertPermissions(UUID.fromString(levelId), tenantId, serviceIds);
            } catch (BadRequestException e) {
                return Response.status(400).entity(Map.of("error", e.getMessage())).build();
            }
        }

        return Response.status(201).entity(Map.of("id", levelId)).build();
    }

    // ─── PUT /{alId} — editar nível de acesso ────────────────────────────────

    @PUT
    @Path("/{alId}")
    @Transactional
    @SuppressWarnings("unchecked")
    public Response updateAccessLevel(
        @PathParam("tenantId") UUID tenantId,
        @PathParam("alId") UUID alId,
        Map<String, Object> body,
        @Context SecurityContext ctx
    ) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        ensureOwnerOrAdmin(tc);

        String name = body != null ? (String) body.get("name") : null;
        if (name == null || name.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Nome é obrigatório")).build();
        }

        Object rawDesc = body.get("description");
        String description = rawDesc != null ? rawDesc.toString().trim() : null;
        List<String> serviceIds = (List<String>) body.get("serviceIds");

        int updated = em.createNativeQuery(
            "UPDATE profile_access_levels " +
            "SET name = :name, description = :description, updated_at = NOW() " +
            "WHERE id = :alId AND tenant_id = :tenantId"
        )
        .setParameter("name", name.trim())
        .setParameter("description", description)
        .setParameter("alId", alId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();

        if (updated == 0) {
            return Response.status(404).entity(Map.of("error", "Nível de acesso não encontrado")).build();
        }

        em.createNativeQuery(
            "DELETE FROM profile_access_level_permissions WHERE access_level_id = :alId"
        ).setParameter("alId", alId).executeUpdate();

        if (serviceIds != null && !serviceIds.isEmpty()) {
            try {
                insertPermissions(alId, tenantId, serviceIds);
            } catch (BadRequestException e) {
                return Response.status(400).entity(Map.of("error", e.getMessage())).build();
            }
        }

        return Response.ok(Map.of("success", true)).build();
    }

    // ─── PATCH /{alId}/status — ativar/inativar ───────────────────────────────

    @PATCH
    @Path("/{alId}/status")
    @Transactional
    public Response toggleStatus(
        @PathParam("tenantId") UUID tenantId,
        @PathParam("alId") UUID alId,
        Map<String, String> body,
        @Context SecurityContext ctx
    ) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        ensureOwnerOrAdmin(tc);

        String status = body != null ? body.get("status") : null;
        if (!List.of("ACTIVE", "INACTIVE").contains(status)) {
            return Response.status(400).entity(Map.of("error", "Status inválido. Use ACTIVE ou INACTIVE")).build();
        }

        int updated = em.createNativeQuery(
            "UPDATE profile_access_levels " +
            "SET status = :status, updated_at = NOW() " +
            "WHERE id = :alId AND tenant_id = :tenantId"
        )
        .setParameter("status", status)
        .setParameter("alId", alId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();

        if (updated == 0) {
            return Response.status(404).entity(Map.of("error", "Nível de acesso não encontrado")).build();
        }

        return Response.ok(Map.of("success", true, "status", status)).build();
    }

    // ─── DELETE /{alId} — excluir (somente se não estiver em uso) ─────────────

    @DELETE
    @Path("/{alId}")
    @Transactional
    public Response deleteAccessLevel(
        @PathParam("tenantId") UUID tenantId,
        @PathParam("alId") UUID alId,
        @Context SecurityContext ctx
    ) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        ensureOwnerOrAdmin(tc);

        long memberCount = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM user_tenants WHERE access_level_id = :alId AND is_active = TRUE"
        ).setParameter("alId", alId).getSingleResult()).longValue();

        if (memberCount > 0) {
            return Response.status(409).entity(Map.of(
                "error", "Este nível está em uso por " + memberCount + " membro(s). Reatribua-os antes de excluir."
            )).build();
        }

        long pendingInvites = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM invitations WHERE access_level_id = :alId AND status = 'pending'"
        ).setParameter("alId", alId).getSingleResult()).longValue();

        if (pendingInvites > 0) {
            return Response.status(409).entity(Map.of(
                "error", "Este nível está referenciado por " + pendingInvites + " convite(s) pendente(s)."
            )).build();
        }

        // Verifica que pertence ao tenant antes de excluir
        int deleted = em.createNativeQuery(
            "DELETE FROM profile_access_levels WHERE id = :alId AND tenant_id = :tenantId"
        )
        .setParameter("alId", alId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();

        if (deleted == 0) {
            return Response.status(404).entity(Map.of("error", "Nível de acesso não encontrado")).build();
        }

        return Response.noContent().build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void insertPermissions(UUID levelId, UUID tenantId, List<String> serviceIds) {
        for (String serviceIdStr : serviceIds) {
            UUID serviceId;
            try {
                serviceId = UUID.fromString(serviceIdStr);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("ID de serviço inválido: " + serviceIdStr);
            }

            long valid = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM platform_module_services pms " +
                "JOIN profile_module_subscriptions sub ON sub.module_id = pms.module_id " +
                "WHERE pms.id = :serviceId AND sub.tenant_id = :tenantId " +
                "  AND sub.status = 'ACTIVE' AND pms.is_active = TRUE"
            ).setParameter("serviceId", serviceId).setParameter("tenantId", tenantId)
             .getSingleResult()).longValue();

            if (valid == 0) {
                throw new BadRequestException("Serviço não disponível para este perfil: " + serviceIdStr);
            }

            String moduleId = (String) em.createNativeQuery(
                "SELECT module_id::text FROM platform_module_services WHERE id = :serviceId"
            ).setParameter("serviceId", serviceId).getSingleResult();

            em.createNativeQuery(
                "INSERT INTO profile_access_level_permissions (access_level_id, module_id, service_id) " +
                "VALUES (:levelId, :moduleId, :serviceId) " +
                "ON CONFLICT (access_level_id, service_id) DO NOTHING"
            )
            .setParameter("levelId", levelId)
            .setParameter("moduleId", UUID.fromString(moduleId))
            .setParameter("serviceId", serviceId)
            .executeUpdate();
        }
    }

    private TenantContext resolveAndCheckAccess(SecurityContext ctx, UUID tenantId) {
        TenantContext tc = TenantContext.from(ctx);
        if (!tc.getTenantId().equals(tenantId)) {
            throw new ForbiddenException("Acesso negado ao tenant");
        }
        return tc;
    }

    private void ensureOwnerOrAdmin(TenantContext tc) {
        if (!List.of("owner", "admin").contains(tc.getUserRole())) {
            throw new ForbiddenException("Apenas proprietários e administradores podem gerenciar níveis de acesso");
        }
    }
}
