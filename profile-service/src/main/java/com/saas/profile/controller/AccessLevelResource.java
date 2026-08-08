package com.saas.profile.controller;

import com.saas.profile.repository.UserTenantRepository;
import com.saas.profile.security.TenantContext;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
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
    EntityManager em;

    @Inject
    UserTenantRepository userTenantRepository;

    // ─── Permissões administrativas fixas do sistema ──────────────────────────

    private static final List<Map<String, Object>> ADMIN_PERMISSION_GROUPS = buildAdminGroups();
    private static final Set<String> ALL_ADMIN_PERMISSION_KEYS = buildAllAdminKeys();

    private static List<Map<String, Object>> buildAdminGroups() {
        return List.of(
            adminGroup("members", "Membros", List.of(
                adminPerm("members.view", "Visualizar membros"),
                adminPerm("members.invite", "Convidar membros"),
                adminPerm("members.remove", "Remover membros"),
                adminPerm("members.change_access_level", "Alterar nível de acesso de membros")
            )),
            adminGroup("access_levels", "Níveis de Acesso", List.of(
                adminPerm("access_levels.view", "Visualizar níveis de acesso"),
                adminPerm("access_levels.create", "Criar nível de acesso"),
                adminPerm("access_levels.edit", "Editar nível de acesso"),
                adminPerm("access_levels.inactivate", "Inativar nível de acesso"),
                adminPerm("access_levels.delete", "Excluir nível de acesso")
            )),
            adminGroup("plans", "Planos", List.of(
                adminPerm("plans.view", "Visualizar planos disponíveis"),
                adminPerm("plans.subscribe", "Contratar módulos/planos")
            )),
            adminGroup("subscriptions", "Assinaturas", List.of(
                adminPerm("subscriptions.view", "Visualizar assinaturas"),
                adminPerm("subscriptions.cancel", "Cancelar assinatura"),
                adminPerm("subscriptions.reactivate", "Reativar assinatura")
            )),
            adminGroup("company_settings", "Configurações da Empresa", List.of(
                adminPerm("company_settings.view", "Visualizar configurações da empresa"),
                adminPerm("company_settings.edit", "Editar dados da empresa")
            )),
            adminGroup("dashboard", "Dashboard", List.of(
                adminPerm("dashboard.view", "Visualizar dashboard")
            )),
            adminGroup("invites", "Convites", List.of(
                adminPerm("invites.view", "Visualizar convites"),
                adminPerm("invites.cancel", "Cancelar convites"),
                adminPerm("invites.resend", "Reenviar convites")
            )),
            adminGroup("billing", "Faturamento", List.of(
                adminPerm("billing.view", "Visualizar faturamento"),
                adminPerm("billing.payment_methods.manage", "Gerenciar formas de pagamento"),
                adminPerm("billing.payment_history.view", "Visualizar histórico de pagamentos")
            ))
        );
    }

    private static Set<String> buildAllAdminKeys() {
        Set<String> keys = new HashSet<>();
        for (Map<String, Object> group : ADMIN_PERMISSION_GROUPS) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> perms = (List<Map<String, Object>>) group.get("permissions");
            for (Map<String, Object> perm : perms) {
                keys.add((String) perm.get("permissionKey"));
            }
        }
        return Set.copyOf(keys);
    }

    private static Map<String, Object> adminGroup(String key, String name, List<Map<String, Object>> perms) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("groupKey", key);
        g.put("groupName", name);
        g.put("permissions", perms);
        return g;
    }

    private static Map<String, Object> adminPerm(String key, String label) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("permissionKey", key);
        p.put("label", label);
        return p;
    }

    // ─── GET /available-modules — árvore completa (módulos + grupos + permissões adm.) ──

    @GET
    @Path("/available-modules")
    @SuppressWarnings("unchecked")
    public Response availableModules(
        @PathParam("tenantId") UUID tenantId,
        @Context SecurityContext ctx
    ) {
        resolveAndCheckAccess(ctx, tenantId);

        // Retorna módulo, grupo (se ativo), serviço.
        // Serviços de grupos inativos aparecem em ungroupedServices.
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "SELECT pm.id::text, pm.name, pm.slug, pm.icon_path, " +
            "  CASE WHEN g.status = 'ACTIVE' THEN g.id::text ELSE NULL END, " +
            "  CASE WHEN g.status = 'ACTIVE' THEN g.name ELSE NULL END, " +
            "  CASE WHEN g.status = 'ACTIVE' THEN g.description ELSE NULL END, " +
            "  CASE WHEN g.status = 'ACTIVE' THEN g.icon_path ELSE NULL END, " +
            "  CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE NULL END, " +
            "  pms.id::text, pms.name, pms.slug, pms.icon_path, pms.sort_order " +
            "FROM profile_module_subscriptions sub " +
            "JOIN platform_modules pm ON pm.id = sub.module_id " +
            "JOIN platform_module_services pms ON pms.module_id = pm.id " +
            "LEFT JOIN platform_module_service_groups g ON g.id = pms.service_group_id " +
            "WHERE sub.tenant_id = :tenantId AND sub.status = 'ACTIVE' " +
            "  AND pm.is_active = TRUE AND pms.is_active = TRUE " +
            "ORDER BY pm.sort_order, " +
            "  CASE WHEN g.status = 'ACTIVE' THEN g.sort_order ELSE 9999 END, " +
            "  pms.sort_order"
        ).setParameter("tenantId", tenantId).getResultList();

        // moduleId → module
        Map<String, Map<String, Object>> moduleMap = new LinkedHashMap<>();
        // moduleId:groupId → group
        Map<String, Map<String, Object>> groupMap = new LinkedHashMap<>();

        for (Object[] row : rows) {
            String moduleId = (String) row[0];
            String groupId  = (String) row[4]; // null se sem grupo ou grupo inativo

            if (!moduleMap.containsKey(moduleId)) {
                Map<String, Object> mod = new LinkedHashMap<>();
                mod.put("moduleId", moduleId);
                mod.put("moduleName", row[1]);
                mod.put("moduleSlug", row[2]);
                mod.put("moduleIconPath", row[3]);
                mod.put("serviceGroups", new ArrayList<Map<String, Object>>());
                mod.put("ungroupedServices", new ArrayList<Map<String, Object>>());
                moduleMap.put(moduleId, mod);
            }

            Map<String, Object> svc = new LinkedHashMap<>();
            svc.put("serviceId",       row[9]);
            svc.put("serviceName",     row[10]);
            svc.put("serviceSlug",     row[11]);
            svc.put("serviceIconPath", row[12]);
            svc.put("sortOrder",       row[13]);

            if (groupId != null) {
                String groupKey = moduleId + ":" + groupId;
                if (!groupMap.containsKey(groupKey)) {
                    Map<String, Object> grp = new LinkedHashMap<>();
                    grp.put("groupId",          groupId);
                    grp.put("groupName",        row[5]);
                    grp.put("groupDescription", row[6]);
                    grp.put("groupIconPath",    row[7]);
                    grp.put("sortOrder",        row[8]);
                    grp.put("services", new ArrayList<Map<String, Object>>());
                    groupMap.put(groupKey, grp);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> moduleGroups =
                        (List<Map<String, Object>>) moduleMap.get(moduleId).get("serviceGroups");
                    moduleGroups.add(grp);
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> groupServices =
                    (List<Map<String, Object>>) groupMap.get(groupKey).get("services");
                groupServices.add(svc);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ungrouped =
                    (List<Map<String, Object>>) moduleMap.get(moduleId).get("ungroupedServices");
                ungrouped.add(svc);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modules", new ArrayList<>(moduleMap.values()));
        result.put("adminPermissions", ADMIN_PERMISSION_GROUPS);

        return Response.ok(result).build();
    }

    // ─── GET / — listar níveis de acesso ─────────────────────────────────────

    @GET
    @SuppressWarnings("unchecked")
    public Response listAccessLevels(
        @PathParam("tenantId") UUID tenantId,
        @Context SecurityContext ctx
    ) {
        TenantContext tc = resolveAndCheckAccess(ctx, tenantId);
        requireAdminPerm(tc, tenantId, "access_levels.view");

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

            List<String> adminPerms = (List<String>) em.createNativeQuery(
                "SELECT permission_key FROM profile_access_level_admin_permissions " +
                "WHERE access_level_id = :levelId ORDER BY created_at"
            ).setParameter("levelId", UUID.fromString(levelId)).getResultList();

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
            m.put("adminPermissions", adminPerms);
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
        requireAdminPerm(tc, tenantId, "access_levels.create");

        String name = body != null ? (String) body.get("name") : null;
        if (name == null || name.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Nome é obrigatório")).build();
        }

        Object rawDesc = body.get("description");
        String description = rawDesc != null ? rawDesc.toString().trim() : null;
        List<String> serviceIds = (List<String>) body.get("serviceIds");
        List<String> adminPermissionKeys = (List<String>) body.get("adminPermissionKeys");

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

        if (adminPermissionKeys != null && !adminPermissionKeys.isEmpty()) {
            try {
                insertAdminPermissions(UUID.fromString(levelId), adminPermissionKeys);
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
        requireAdminPerm(tc, tenantId, "access_levels.edit");

        String name = body != null ? (String) body.get("name") : null;
        if (name == null || name.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Nome é obrigatório")).build();
        }

        Object rawDesc = body.get("description");
        String description = rawDesc != null ? rawDesc.toString().trim() : null;
        List<String> serviceIds = (List<String>) body.get("serviceIds");
        List<String> adminPermissionKeys = (List<String>) body.get("adminPermissionKeys");

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

        em.createNativeQuery(
            "DELETE FROM profile_access_level_admin_permissions WHERE access_level_id = :alId"
        ).setParameter("alId", alId).executeUpdate();

        if (serviceIds != null && !serviceIds.isEmpty()) {
            try {
                insertPermissions(alId, tenantId, serviceIds);
            } catch (BadRequestException e) {
                return Response.status(400).entity(Map.of("error", e.getMessage())).build();
            }
        }

        if (adminPermissionKeys != null && !adminPermissionKeys.isEmpty()) {
            try {
                insertAdminPermissions(alId, adminPermissionKeys);
            } catch (BadRequestException e) {
                return Response.status(400).entity(Map.of("error", e.getMessage())).build();
            }
        }

        // Permissões do nível mudaram — invalida PAT/MAT em cache de todos os membros vinculados.
        userTenantRepository.bumpVersionForAccessLevel(alId);

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
        requireAdminPerm(tc, tenantId, "access_levels.inactivate");

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

        // Status do nível mudou (ex.: INACTIVE) — invalida PAT/MAT em cache dos membros vinculados.
        userTenantRepository.bumpVersionForAccessLevel(alId);

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
        requireAdminPerm(tc, tenantId, "access_levels.delete");

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

    private void insertAdminPermissions(UUID levelId, List<String> keys) {
        for (String key : keys) {
            if (!ALL_ADMIN_PERMISSION_KEYS.contains(key)) {
                throw new BadRequestException("Permissão administrativa inválida: " + key);
            }
            em.createNativeQuery(
                "INSERT INTO profile_access_level_admin_permissions (access_level_id, permission_key) " +
                "VALUES (:levelId, :key) ON CONFLICT (access_level_id, permission_key) DO NOTHING"
            )
            .setParameter("levelId", levelId)
            .setParameter("key", key)
            .executeUpdate();
        }
    }

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
