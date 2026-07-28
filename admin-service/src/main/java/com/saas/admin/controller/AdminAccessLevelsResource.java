package com.saas.admin.controller;

import com.saas.admin.security.AdminAuthService;
import com.saas.admin.service.AdminAuditService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CRUD de níveis de acesso administrativo. Migrado 1:1 do backend-quarkus
 * (com.saas.resource.AdminAccessLevelsResource).
 */
@Path("/api/v1/admin/access-levels")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminAccessLevelsResource {

    @Inject EntityManager em;
    @Inject JsonWebToken  jwt;
    @Inject AdminAuthService adminAuth;
    @Inject AdminAuditService auditService;

    // ─── Árvore fixa de permissões administrativas ────────────────────────────

    static final List<Map<String, Object>> PERMISSION_GROUPS = buildGroups();

    private static List<Map<String, Object>> buildGroups() {
        return List.of(
            group("dashboard", "Dashboard", List.of(
                perm("admin.dashboard.view", "Visualizar dashboard")
            )),
            group("clients", "Clientes", List.of(
                perm("admin.clients.view",       "Visualizar clientes"),
                perm("admin.clients.detail",     "Ver detalhe do cliente"),
                perm("admin.clients.edit",       "Editar cliente"),
                perm("admin.clients.activate",   "Ativar cliente"),
                perm("admin.clients.deactivate", "Inativar cliente")
            )),
            group("companies", "Empresas", List.of(
                perm("admin.companies.view",       "Visualizar empresas"),
                perm("admin.companies.detail",     "Ver detalhe da empresa"),
                perm("admin.companies.edit",       "Editar empresa"),
                perm("admin.companies.activate",   "Ativar empresa"),
                perm("admin.companies.deactivate", "Inativar empresa")
            )),
            group("plans", "Planos", List.of(
                perm("admin.plans.view",           "Visualizar planos"),
                perm("admin.plans.create",         "Criar plano"),
                perm("admin.plans.edit",           "Editar plano"),
                perm("admin.plans.create_version", "Criar nova versão"),
                perm("admin.plans.version_history","Histórico de versões"),
                perm("admin.plans.activate",       "Ativar/inativar plano")
            )),
            group("subscriptions", "Assinaturas", List.of(
                perm("admin.subscriptions.view",       "Visualizar assinaturas"),
                perm("admin.subscriptions.cancel",     "Cancelar assinatura"),
                perm("admin.subscriptions.reactivate", "Reativar assinatura")
            )),
            group("modules", "Módulos", List.of(
                perm("admin.modules.view",               "Visualizar módulos"),
                perm("admin.modules.create",             "Criar módulo"),
                perm("admin.modules.edit",               "Editar módulo"),
                perm("admin.modules.activate",           "Ativar módulo"),
                perm("admin.modules.deactivate",         "Inativar módulo"),
                perm("admin.services.groups.view",       "Visualizar grupos de serviços"),
                perm("admin.services.groups.create",     "Criar grupo de serviço"),
                perm("admin.services.groups.edit",       "Editar grupo de serviço"),
                perm("admin.services.groups.activate",   "Ativar grupo de serviço"),
                perm("admin.services.groups.deactivate", "Inativar grupo de serviço"),
                perm("admin.services.view",              "Visualizar serviços"),
                perm("admin.services.create",            "Criar serviço"),
                perm("admin.services.edit",              "Editar serviço"),
                perm("admin.services.activate",          "Ativar serviço"),
                perm("admin.services.deactivate",        "Inativar serviço")
            )),
            group("users", "Usuários Administrativos", List.of(
                perm("admin.users.view",           "Visualizar usuários administrativos"),
                perm("admin.users.create",         "Criar usuário administrativo"),
                perm("admin.users.edit",           "Editar usuário administrativo"),
                perm("admin.users.activate",       "Ativar/inativar usuário administrativo"),
                perm("admin.users.reset_password", "Resetar senha de usuário administrativo")
            )),
            group("access_levels", "Níveis de Acesso Admin", List.of(
                perm("admin.access_levels.view",       "Visualizar níveis de acesso"),
                perm("admin.access_levels.create",     "Criar nível de acesso"),
                perm("admin.access_levels.edit",       "Editar nível de acesso"),
                perm("admin.access_levels.activate",   "Ativar/inativar nível de acesso")
            )),
            group("trials", "Trials", List.of(
                perm("admin.trials.view",   "Visualizar Trials"),
                perm("admin.trials.create", "Criar campanha de Trial"),
                perm("admin.trials.edit",   "Editar campanha de Trial"),
                perm("admin.trials.cancel", "Cancelar campanha de Trial")
            )),
            group("settings", "Configurações da Plataforma", List.of(
                perm("admin.settings.view", "Visualizar configurações"),
                perm("admin.settings.edit", "Editar configurações")
            )),
            group("feature_flags", "Feature Flags", List.of(
                perm("admin.feature_flags.view",     "Visualizar feature flags"),
                perm("admin.feature_flags.create",   "Criar feature flag"),
                perm("admin.feature_flags.edit",     "Editar feature flag"),
                perm("admin.feature_flags.activate", "Ativar/desativar feature flag")
            ))
        );
    }

    private static Map<String, Object> group(String key, String name, List<Map<String, Object>> perms) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("groupKey", key);
        m.put("groupName", name);
        m.put("permissions", perms);
        return m;
    }

    private static Map<String, Object> perm(String key, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("permissionKey", key);
        m.put("label", label);
        return m;
    }

    // ─── Endpoints ───────────────────────────────────────────────────────────

    /**
     * Retorna as permissões do próprio usuário autenticado.
     * Não exige nenhuma permissão específica além de ser um ADMIN_USER ativo —
     * evita o ciclo onde carregar permissões no login exige uma permissão que
     * ainda não foi carregada.
     */
    @GET
    @Path("/my-permissions")
    @SuppressWarnings("unchecked")
    public Response getMyPermissions() {
        adminAuth.requireAdminPermission(null);
        String userId = jwt.getSubject();

        List<String> accessLevelIds = (List<String>) em.createNativeQuery(
            "SELECT admin_access_level_id::text FROM user_profiles WHERE id::text = :id"
        ).setParameter("id", userId).getResultList();

        if (accessLevelIds.isEmpty() || accessLevelIds.get(0) == null) {
            return Response.ok(Map.of("permissionKeys", List.of())).build();
        }

        String accessLevelId = accessLevelIds.get(0);
        List<String> perms = (List<String>) em.createNativeQuery(
            "SELECT permission_key FROM admin_access_level_permissions " +
            "WHERE access_level_id::text = :id ORDER BY permission_key"
        ).setParameter("id", accessLevelId).getResultList();

        return Response.ok(Map.of("permissionKeys", perms)).build();
    }

    @GET
    @Path("/permission-tree")
    public Response getPermissionTree() {
        adminAuth.requireAdminPermission("admin.access_levels.view");
        return Response.ok(Map.of("groups", PERMISSION_GROUPS)).build();
    }

    @GET
    @SuppressWarnings("unchecked")
    public Response listAccessLevels(@QueryParam("status") String status) {
        adminAuth.requireAdminPermission("admin.access_levels.view");

        StringBuilder sql = new StringBuilder(
            "SELECT al.id::text, al.name, al.description, al.status, " +
            "al.created_at::text, al.updated_at::text, " +
            "(SELECT COUNT(*) FROM admin_access_level_permissions p WHERE p.access_level_id = al.id)::int AS perm_count, " +
            "(SELECT COUNT(*) FROM user_profiles up WHERE up.admin_access_level_id = al.id AND up.system_role = 'ADMIN_USER')::int AS user_count " +
            "FROM admin_access_levels al WHERE 1=1"
        );
        if (status != null && !status.isBlank()) {
            sql.append(" AND al.status = '").append(status.toUpperCase().trim()).append("'");
        }
        sql.append(" ORDER BY al.name");

        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(sql.toString()).getResultList();
        var levels = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",          r[0]);
            m.put("name",        r[1]);
            m.put("description", r[2]);
            m.put("status",      r[3]);
            m.put("createdAt",   r[4]);
            m.put("updatedAt",   r[5]);
            m.put("permCount",   r[6]);
            m.put("userCount",   r[7]);
            return m;
        }).toList();
        return Response.ok(levels).build();
    }

    @GET
    @Path("/{id}")
    @SuppressWarnings("unchecked")
    public Response getAccessLevel(@PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.access_levels.view");

        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "SELECT id::text, name, description, status, created_at::text, updated_at::text " +
            "FROM admin_access_levels WHERE id::text = :id"
        ).setParameter("id", id).getResultList();

        if (rows.isEmpty()) return Response.status(404).entity(Map.of("error", "Nível não encontrado")).build();
        Object[] r = rows.get(0);

        List<String> perms = (List<String>) em.createNativeQuery(
            "SELECT permission_key FROM admin_access_level_permissions WHERE access_level_id::text = :id ORDER BY permission_key"
        ).setParameter("id", id).getResultList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",              r[0]);
        m.put("name",            r[1]);
        m.put("description",     r[2]);
        m.put("status",          r[3]);
        m.put("createdAt",       r[4]);
        m.put("updatedAt",       r[5]);
        m.put("permissionKeys",  perms);
        return Response.ok(m).build();
    }

    @POST
    @Transactional
    @SuppressWarnings("unchecked")
    public Response createAccessLevel(Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.access_levels.create");

        String name = (String) body.get("name");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();

        String description = (String) body.get("description");
        String status = "ACTIVE";

        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "INSERT INTO admin_access_levels (name, description, status) " +
            "VALUES (:name, :description, :status) " +
            "RETURNING id::text, name, description, status, created_at::text, updated_at::text"
        ).setParameter("name", name.trim())
         .setParameter("description", description)
         .setParameter("status", status)
         .getResultList();

        Object[] r = rows.get(0);
        String levelId = (String) r[0];

        List<String> permKeys = extractPermKeys(body);
        savePermissions(levelId, permKeys);
        auditService.log(adminAuth.currentUserId(), "access_level.create", "admin_access_levels", levelId, Map.of("name", name.trim()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",             levelId);
        m.put("name",           r[1]);
        m.put("description",    r[2]);
        m.put("status",         r[3]);
        m.put("createdAt",      r[4]);
        m.put("updatedAt",      r[5]);
        m.put("permissionKeys", permKeys);
        return Response.status(201).entity(m).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @SuppressWarnings("unchecked")
    public Response updateAccessLevel(@PathParam("id") String id, Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.access_levels.edit");

        String name = (String) body.get("name");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();

        int updated = em.createNativeQuery(
            "UPDATE admin_access_levels SET name = :name, description = :description WHERE id::text = :id"
        ).setParameter("name", name.trim())
         .setParameter("description", body.get("description"))
         .setParameter("id", id)
         .executeUpdate();

        if (updated == 0) return Response.status(404).entity(Map.of("error", "Nível não encontrado")).build();

        em.createNativeQuery(
            "DELETE FROM admin_access_level_permissions WHERE access_level_id::text = :id"
        ).setParameter("id", id).executeUpdate();

        List<String> permKeys = extractPermKeys(body);
        savePermissions(id, permKeys);
        auditService.log(adminAuth.currentUserId(), "access_level.update", "admin_access_levels", id, Map.of("name", name.trim()));

        return Response.ok(Map.of("ok", true, "permissionKeys", permKeys)).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Transactional
    public Response updateStatus(@PathParam("id") String id, Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.access_levels.activate");

        String newStatus = body instanceof Map<?,?> ? (String) body.get("status") : null;
        if (!List.of("ACTIVE", "INACTIVE").contains(newStatus))
            return Response.status(400).entity(Map.of("error", "Status inválido. Use ACTIVE ou INACTIVE")).build();

        if ("INACTIVE".equals(newStatus)) {
            long userCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM user_profiles WHERE admin_access_level_id::text = :id AND system_role = 'ADMIN_USER' AND is_active = TRUE"
            ).setParameter("id", id).getSingleResult()).longValue();
            if (userCount > 0)
                return Response.status(409).entity(Map.of(
                    "error", "Este nível possui " + userCount + " usuário(s) ativo(s). Reatribua-os antes de inativar.",
                    "user_count", userCount
                )).build();
        }

        int updated = em.createNativeQuery(
            "UPDATE admin_access_levels SET status = :status WHERE id::text = :id"
        ).setParameter("status", newStatus).setParameter("id", id).executeUpdate();

        if (updated == 0) return Response.status(404).entity(Map.of("error", "Nível não encontrado")).build();
        auditService.log(adminAuth.currentUserId(), "access_level.status_change", "admin_access_levels", id, Map.of("status", newStatus));
        return Response.ok(Map.of("ok", true, "status", newStatus)).build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String> extractPermKeys(Map<String, Object> body) {
        Object raw = body.get("permissionKeys");
        if (!(raw instanceof List<?> list)) return List.of();

        Set<String> validKeys = PERMISSION_GROUPS.stream()
            .flatMap(g -> ((List<Map<String, Object>>) g.get("permissions")).stream())
            .map(p -> (String) p.get("permissionKey"))
            .collect(Collectors.toSet());

        return list.stream()
            .filter(k -> k instanceof String && validKeys.contains(k))
            .map(k -> (String) k)
            .distinct()
            .collect(Collectors.toList());
    }

    private void savePermissions(String levelId, List<String> permKeys) {
        for (String key : permKeys) {
            em.createNativeQuery(
                "INSERT INTO admin_access_level_permissions (access_level_id, permission_key) " +
                "VALUES (CAST(:levelId AS uuid), :key) ON CONFLICT DO NOTHING"
            ).setParameter("levelId", levelId).setParameter("key", key).executeUpdate();
        }
    }
}
