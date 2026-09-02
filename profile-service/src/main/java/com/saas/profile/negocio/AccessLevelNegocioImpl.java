package com.saas.profile.negocio;

import com.saas.profile.dao.AccessLevelDAO;
import com.saas.profile.dao.UserTenantDAO;
import com.saas.profile.dto.accesslevel.*;
import com.saas.profile.dto.request.AccessLevelRequest;
import com.saas.profile.exception.ConflictException;
import com.saas.profile.to.ModuleServiceTO;
import com.saas.profile.negocio.impl.AccessLevelNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class AccessLevelNegocioImpl implements AccessLevelNegocio {

    @Inject
    AccessLevelDAO accessLevelDAO;

    @Inject
    UserTenantDAO userTenantDAO;

    // ─── Catálogo fixo de permissões administrativas do sistema ──────────────

    private static final List<AdminPermissionGroupDto> ADMIN_PERMISSION_GROUPS = buildAdminGroups();
    private static final Set<String> ALL_ADMIN_PERMISSION_KEYS = buildAllAdminKeys();

    private static List<AdminPermissionGroupDto> buildAdminGroups() {
        return List.of(
                new AdminPermissionGroupDto("members", "Membros", List.of(
                        new AdminPermissionDto("members.view", "Visualizar membros"),
                        new AdminPermissionDto("members.invite", "Convidar membros"),
                        new AdminPermissionDto("members.remove", "Remover membros"),
                        new AdminPermissionDto("members.change_access_level", "Alterar nível de acesso de membros")
                )),
                new AdminPermissionGroupDto("access_levels", "Níveis de Acesso", List.of(
                        new AdminPermissionDto("access_levels.view", "Visualizar níveis de acesso"),
                        new AdminPermissionDto("access_levels.create", "Criar nível de acesso"),
                        new AdminPermissionDto("access_levels.edit", "Editar nível de acesso"),
                        new AdminPermissionDto("access_levels.inactivate", "Inativar nível de acesso"),
                        new AdminPermissionDto("access_levels.delete", "Excluir nível de acesso")
                )),
                new AdminPermissionGroupDto("plans", "Planos", List.of(
                        new AdminPermissionDto("plans.view", "Visualizar planos disponíveis"),
                        new AdminPermissionDto("plans.subscribe", "Contratar módulos/planos")
                )),
                new AdminPermissionGroupDto("subscriptions", "Assinaturas", List.of(
                        new AdminPermissionDto("subscriptions.view", "Visualizar assinaturas"),
                        new AdminPermissionDto("subscriptions.cancel", "Cancelar assinatura"),
                        new AdminPermissionDto("subscriptions.reactivate", "Reativar assinatura")
                )),
                new AdminPermissionGroupDto("company_settings", "Configurações da Empresa", List.of(
                        new AdminPermissionDto("company_settings.view", "Visualizar configurações da empresa"),
                        new AdminPermissionDto("company_settings.edit", "Editar dados da empresa")
                )),
                new AdminPermissionGroupDto("dashboard", "Dashboard", List.of(
                        new AdminPermissionDto("dashboard.view", "Visualizar dashboard")
                )),
                new AdminPermissionGroupDto("invites", "Convites", List.of(
                        new AdminPermissionDto("invites.view", "Visualizar convites"),
                        new AdminPermissionDto("invites.cancel", "Cancelar convites"),
                        new AdminPermissionDto("invites.resend", "Reenviar convites")
                )),
                new AdminPermissionGroupDto("billing", "Faturamento", List.of(
                        new AdminPermissionDto("billing.view", "Visualizar faturamento"),
                        new AdminPermissionDto("billing.payment_methods.manage", "Gerenciar formas de pagamento"),
                        new AdminPermissionDto("billing.payment_history.view", "Visualizar histórico de pagamentos")
                ))
        );
    }

    private static Set<String> buildAllAdminKeys() {
        Set<String> keys = new HashSet<>();
        for (AdminPermissionGroupDto group : ADMIN_PERMISSION_GROUPS) {
            for (AdminPermissionDto perm : group.permissions()) {
                keys.add(perm.permissionKey());
            }
        }
        return Set.copyOf(keys);
    }

    // ─── available-modules ─────────────────────────────────────────────────

    public AvailableModulesResponse availableModules(UUID tenantId) {
        List<ModuleServiceTO> rows = accessLevelDAO.findAvailableModuleTree(tenantId);

        Map<String, ModuleBuilder> moduleMap = new LinkedHashMap<>();
        Map<String, GroupBuilder> groupMap = new LinkedHashMap<>();

        for (ModuleServiceTO row : rows) {
            String moduleId = row.moduleId();
            String groupId = row.groupId(); // null se sem grupo ou grupo inativo

            ModuleBuilder mod = moduleMap.computeIfAbsent(moduleId, id -> new ModuleBuilder(row));

            ServiceDto svc = new ServiceDto(row.serviceId(), row.serviceName(), row.serviceSlug(), row.serviceIconPath(), row.serviceSortOrder());

            if (groupId != null) {
                String groupKey = moduleId + ":" + groupId;
                GroupBuilder grp = groupMap.get(groupKey);
                if (grp == null) {
                    grp = new GroupBuilder(row);
                    groupMap.put(groupKey, grp);
                    mod.groups.add(grp);
                }
                grp.services.add(svc);
            } else {
                mod.ungroupedServices.add(svc);
            }
        }

        List<ModuleTreeDto> modules = moduleMap.values().stream()
                .map(ModuleBuilder::toDto)
                .collect(Collectors.toList());

        return new AvailableModulesResponse(modules, ADMIN_PERMISSION_GROUPS);
    }

    private static class ModuleBuilder {
        final String moduleId, moduleName, moduleSlug, moduleIconPath;
        final List<GroupBuilder> groups = new ArrayList<>();
        final List<ServiceDto> ungroupedServices = new ArrayList<>();

        ModuleBuilder(ModuleServiceTO row) {
            this.moduleId = row.moduleId();
            this.moduleName = row.moduleName();
            this.moduleSlug = row.moduleSlug();
            this.moduleIconPath = row.moduleIconPath();
        }

        ModuleTreeDto toDto() {
            List<ServiceGroupDto> groupDtos = groups.stream().map(GroupBuilder::toDto).toList();
            return new ModuleTreeDto(moduleId, moduleName, moduleSlug, moduleIconPath, groupDtos, ungroupedServices);
        }
    }

    private static class GroupBuilder {
        final String groupId, groupName, groupDescription, groupIconPath;
        final Integer sortOrder;
        final List<ServiceDto> services = new ArrayList<>();

        GroupBuilder(ModuleServiceTO row) {
            this.groupId = row.groupId();
            this.groupName = row.groupName();
            this.groupDescription = row.groupDescription();
            this.groupIconPath = row.groupIconPath();
            this.sortOrder = row.groupSortOrder();
        }

        ServiceGroupDto toDto() {
            return new ServiceGroupDto(groupId, groupName, groupDescription, groupIconPath, sortOrder, services);
        }
    }

    // ─── CRUD de níveis de acesso ──────────────────────────────────────────

    public List<AccessLevelDto> listAccessLevels(UUID tenantId) {
        return accessLevelDAO.findAllByTenant(tenantId).stream().map(level -> {
            UUID levelId = UUID.fromString(level.id());

            List<AccessLevelPermissionDto> permissions = accessLevelDAO.findPermissions(levelId).stream()
                    .map(p -> new AccessLevelPermissionDto(p.id(), p.moduleId(), p.serviceId()))
                    .toList();

            List<String> adminPerms = accessLevelDAO.findAdminPermissionKeys(levelId);
            long memberCount = accessLevelDAO.countActiveMembers(levelId);

            return new AccessLevelDto(level.id(), level.name(), level.description(), level.status(),
                    level.createdAt(), level.updatedAt(), permissions, adminPerms, memberCount);
        }).collect(Collectors.toList());
    }

    @Transactional
    public String createAccessLevel(UUID tenantId, AccessLevelRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Nome é obrigatório");
        }
        String description = request.description() != null ? request.description().trim() : null;

        String levelId = accessLevelDAO.insertAccessLevel(tenantId, request.name().trim(), description);

        if (request.serviceIds() != null && !request.serviceIds().isEmpty()) {
            insertPermissions(UUID.fromString(levelId), tenantId, request.serviceIds());
        }
        if (request.adminPermissionKeys() != null && !request.adminPermissionKeys().isEmpty()) {
            insertAdminPermissions(UUID.fromString(levelId), request.adminPermissionKeys());
        }

        return levelId;
    }

    @Transactional
    public void updateAccessLevel(UUID tenantId, UUID alId, AccessLevelRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Nome é obrigatório");
        }
        String description = request.description() != null ? request.description().trim() : null;

        boolean updated = accessLevelDAO.updateAccessLevel(tenantId, alId, request.name().trim(), description);
        if (!updated) {
            throw new NotFoundException("Nível de acesso não encontrado");
        }

        accessLevelDAO.deletePermissions(alId);
        accessLevelDAO.deleteAdminPermissions(alId);

        if (request.serviceIds() != null && !request.serviceIds().isEmpty()) {
            insertPermissions(alId, tenantId, request.serviceIds());
        }
        if (request.adminPermissionKeys() != null && !request.adminPermissionKeys().isEmpty()) {
            insertAdminPermissions(alId, request.adminPermissionKeys());
        }

        // Permissões do nível mudaram — invalida PAT/MAT em cache de todos os membros vinculados.
        userTenantDAO.bumpVersionForAccessLevel(alId);
    }

    @Transactional
    public String updateStatus(UUID tenantId, UUID alId, String status) {
        if (!List.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new BadRequestException("Status inválido. Use ACTIVE ou INACTIVE");
        }

        boolean updated = accessLevelDAO.updateStatus(tenantId, alId, status);
        if (!updated) {
            throw new NotFoundException("Nível de acesso não encontrado");
        }

        // Status do nível mudou (ex.: INACTIVE) — invalida PAT/MAT em cache dos membros vinculados.
        userTenantDAO.bumpVersionForAccessLevel(alId);
        return status;
    }

    @Transactional
    public void deleteAccessLevel(UUID tenantId, UUID alId) {
        long memberCount = accessLevelDAO.countActiveMembers(alId);
        if (memberCount > 0) {
            throw new ConflictException("Este nível está em uso por " + memberCount + " membro(s). Reatribua-os antes de excluir.");
        }

        long pendingInvites = accessLevelDAO.countPendingInvites(alId);
        if (pendingInvites > 0) {
            throw new ConflictException("Este nível está referenciado por " + pendingInvites + " convite(s) pendente(s).");
        }

        boolean deleted = accessLevelDAO.deleteAccessLevel(tenantId, alId);
        if (!deleted) {
            throw new NotFoundException("Nível de acesso não encontrado");
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private void insertPermissions(UUID levelId, UUID tenantId, List<String> serviceIds) {
        for (String serviceIdStr : serviceIds) {
            UUID serviceId;
            try {
                serviceId = UUID.fromString(serviceIdStr);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("ID de serviço inválido: " + serviceIdStr);
            }

            if (!accessLevelDAO.isServiceAvailableForTenant(serviceId, tenantId)) {
                throw new BadRequestException("Serviço não disponível para este perfil: " + serviceIdStr);
            }

            String moduleId = accessLevelDAO.findModuleIdForService(serviceId);
            accessLevelDAO.insertPermission(levelId, UUID.fromString(moduleId), serviceId);
        }
    }

    private void insertAdminPermissions(UUID levelId, List<String> keys) {
        for (String key : keys) {
            if (!ALL_ADMIN_PERMISSION_KEYS.contains(key)) {
                throw new BadRequestException("Permissão administrativa inválida: " + key);
            }
            accessLevelDAO.insertAdminPermission(levelId, key);
        }
    }
}
