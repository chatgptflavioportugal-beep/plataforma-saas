package com.saas.admin.service;

import com.saas.admin.dao.AccessLevelDAO;
import com.saas.admin.dto.AccessLevelDTO;
import com.saas.admin.dto.AccessLevelDetailDTO;
import com.saas.admin.dto.PermissionGroupDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class AccessLevelService {

    @Inject
    AccessLevelDAO dao;

    @Inject
    AdminAuditService auditService;

    // ─── Árvore fixa de permissões administrativas ────────────────────────────

    public static final List<PermissionGroupDTO> PERMISSION_GROUPS = buildGroups();

    private static List<PermissionGroupDTO> buildGroups() {
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

    private static PermissionGroupDTO group(String key, String name, List<PermissionGroupDTO.PermissionDTO> perms) {
        return new PermissionGroupDTO(key, name, perms);
    }

    private static PermissionGroupDTO.PermissionDTO perm(String key, String label) {
        return new PermissionGroupDTO.PermissionDTO(key, label);
    }

    // ─── Consultas ───────────────────────────────────────────────────────────

    public List<PermissionGroupDTO> getPermissionTree() {
        return PERMISSION_GROUPS;
    }

    public List<String> getMyPermissionKeys(String userId) {
        Optional<String> accessLevelId = dao.findAccessLevelIdForUser(userId);
        if (accessLevelId.isEmpty() || accessLevelId.get() == null) return List.of();
        return dao.findPermissionKeys(accessLevelId.get());
    }

    public List<AccessLevelDTO> list(String status) {
        return dao.findAll(status);
    }

    public Optional<AccessLevelDetailDTO> get(String id) {
        List<String> permKeys = dao.findPermissionKeys(id);
        return dao.findById(id, permKeys);
    }

    public long countActiveAdminUsers(String accessLevelId) {
        return dao.countActiveAdminUsers(accessLevelId);
    }

    // ─── Mutações ────────────────────────────────────────────────────────────

    @Transactional
    public AccessLevelDetailDTO create(String name, String description, List<Object> rawPermissionKeys, String actorUserId) {
        List<String> permKeys = extractPermKeys(rawPermissionKeys);
        AccessLevelDetailDTO created = dao.insert(name, description, "ACTIVE", permKeys);
        dao.savePermissions(created.id(), permKeys);

        auditService.log(actorUserId, "access_level.create", "admin_access_levels", created.id(), Map.of("name", name));
        return created;
    }

    @Transactional
    public Optional<List<String>> update(String id, String name, String description, List<Object> rawPermissionKeys, String actorUserId) {
        int updated = dao.updateNameDescription(id, name, description);
        if (updated == 0) return Optional.empty();

        dao.deletePermissions(id);
        List<String> permKeys = extractPermKeys(rawPermissionKeys);
        dao.savePermissions(id, permKeys);

        auditService.log(actorUserId, "access_level.update", "admin_access_levels", id, Map.of("name", name));
        return Optional.of(permKeys);
    }

    @Transactional
    public boolean updateStatus(String id, String status, String actorUserId) {
        int updated = dao.updateStatus(id, status);
        if (updated == 0) return false;

        auditService.log(actorUserId, "access_level.status_change", "admin_access_levels", id, Map.of("status", status));
        return true;
    }

    private List<String> extractPermKeys(List<Object> raw) {
        if (raw == null) return List.of();

        Set<String> validKeys = PERMISSION_GROUPS.stream()
            .flatMap(g -> g.permissions().stream())
            .map(PermissionGroupDTO.PermissionDTO::permissionKey)
            .collect(Collectors.toSet());

        return raw.stream()
            .filter(k -> k instanceof String && validKeys.contains(k))
            .map(k -> (String) k)
            .distinct()
            .collect(Collectors.toList());
    }
}
