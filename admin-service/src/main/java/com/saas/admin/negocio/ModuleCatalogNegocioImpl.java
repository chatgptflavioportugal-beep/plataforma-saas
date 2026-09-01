package com.saas.admin.negocio;

import com.saas.admin.dao.ModuleCatalogDAO;
import com.saas.admin.dto.ModuleRequest;
import com.saas.admin.dto.ModuleServiceGroupRequest;
import com.saas.admin.dto.ModuleServiceRequest;
import com.saas.admin.dto.PlatformModuleDTO;
import com.saas.admin.dto.PlatformModuleServiceDTO;
import com.saas.admin.dto.PlatformModuleServiceGroupDTO;
import com.saas.admin.negocio.impl.ModuleCatalogNegocio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class ModuleCatalogNegocioImpl implements ModuleCatalogNegocio {

    private static final Pattern SLUG_PATTERN =
        Pattern.compile("^[a-z0-9][a-z0-9_-]*$|^[a-z0-9]$");

    @Inject
    ModuleCatalogDAO dao;

    @Override
    public boolean isValidSlug(String slug) {
        return slug != null && !slug.isBlank() && SLUG_PATTERN.matcher(slug).matches();
    }

    private static String generateRouteKey(String moduleSlug, String groupSlug, String serviceSlug) {
        String permKey = (groupSlug != null && !groupSlug.isBlank())
            ? moduleSlug + "." + groupSlug + "." + serviceSlug
            : moduleSlug + "." + serviceSlug;
        return permKey.toLowerCase()
            .replaceAll("[._\\s]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-+|-+$", "");
    }

    // ─── Módulos ───────────────────────────────────────────────────────────

    @Override
    public List<PlatformModuleDTO> listModules(String search, Boolean isActive) {
        return dao.findModules(search, isActive);
    }

    @Override
    @Transactional
    public OpResult createModule(ModuleRequest req) {
        if (req.name() == null || req.name().isBlank())
            return OpResult.error(400, "name é obrigatório");
        if (req.slug() == null || req.slug().isBlank())
            return OpResult.error(400, "slug é obrigatório");
        if (!isValidSlug(req.slug()))
            return OpResult.error(400, "Slug inválido. Use apenas letras minúsculas, números e hífen");
        if (req.moduleUrl() == null || req.moduleUrl().isBlank())
            return OpResult.error(400, "module_url é obrigatório");

        if (dao.countModuleBySlug(req.slug()) > 0)
            return OpResult.error(400, "Já existe um módulo com este slug");

        return OpResult.ok(201, dao.insertModule(req));
    }

    @Override
    @Transactional
    public OpResult updateModule(String id, ModuleRequest req) {
        if (req.name() == null || req.name().isBlank())
            return OpResult.error(400, "name é obrigatório");
        if (req.slug() == null || req.slug().isBlank())
            return OpResult.error(400, "slug é obrigatório");
        if (!isValidSlug(req.slug()))
            return OpResult.error(400, "Slug inválido. Use apenas letras minúsculas, números e hífen");
        if (req.moduleUrl() == null || req.moduleUrl().isBlank())
            return OpResult.error(400, "module_url é obrigatório");

        if (dao.countModuleBySlugExcluding(req.slug(), id) > 0)
            return OpResult.error(400, "Já existe outro módulo com este slug");

        int updated = dao.updateModule(id, req);
        if (updated == 0) return OpResult.error(404, "Módulo não encontrado");
        return OpResult.ok(200, Map.of("ok", true));
    }

    @Override
    @Transactional
    public boolean toggleModuleStatus(String id) {
        return dao.toggleModuleStatus(id) > 0;
    }

    // ─── Serviços ──────────────────────────────────────────────────────────

    @Override
    public List<PlatformModuleServiceDTO> listServices(String moduleId) {
        return dao.findServices(moduleId);
    }

    @Override
    @Transactional
    public OpResult createService(String moduleId, ModuleServiceRequest req) {
        if (req.name() == null || req.name().isBlank())
            return OpResult.error(400, "name é obrigatório");
        if (req.slug() == null || req.slug().isBlank())
            return OpResult.error(400, "slug é obrigatório");
        if (!isValidSlug(req.slug()))
            return OpResult.error(400, "Slug inválido. Use apenas letras minúsculas, números e hífen");

        if (dao.countModuleById(moduleId) == 0)
            return OpResult.error(404, "Módulo não encontrado");

        if (dao.countServiceBySlug(moduleId, req.slug()) > 0)
            return OpResult.error(400, "Já existe um serviço com este slug neste módulo");

        UUID serviceGroupId = null;
        if (req.serviceGroupId() != null && !req.serviceGroupId().isBlank()) {
            try {
                serviceGroupId = UUID.fromString(req.serviceGroupId());
            } catch (IllegalArgumentException e) {
                return OpResult.error(400, "service_group_id inválido");
            }
            if (dao.countServiceGroupInModule(serviceGroupId, moduleId) == 0)
                return OpResult.error(400, "Grupo não encontrado neste módulo");
        }

        String moduleSlug = dao.findModuleSlug(moduleId);
        String groupSlug = serviceGroupId != null ? dao.findGroupSlug(serviceGroupId) : null;
        String routeKey = generateRouteKey(moduleSlug, groupSlug, req.slug());

        if (dao.countServiceByRouteKey(routeKey) > 0)
            return OpResult.error(400, "Já existe um serviço com este Route Key: " + routeKey);

        return OpResult.ok(201, dao.insertService(moduleId, req, serviceGroupId, routeKey));
    }

    @Override
    @Transactional
    public OpResult updateService(String moduleId, String id, ModuleServiceRequest req) {
        if (req.name() == null || req.name().isBlank())
            return OpResult.error(400, "name é obrigatório");
        if (req.slug() == null || req.slug().isBlank())
            return OpResult.error(400, "slug é obrigatório");
        if (!isValidSlug(req.slug()))
            return OpResult.error(400, "Slug inválido. Use apenas letras minúsculas, números e hífen");

        if (dao.countServiceBySlugExcluding(moduleId, req.slug(), id) > 0)
            return OpResult.error(400, "Já existe outro serviço com este slug neste módulo");

        UUID serviceGroupId = null;
        if (req.serviceGroupId() != null && !req.serviceGroupId().isBlank()) {
            try {
                serviceGroupId = UUID.fromString(req.serviceGroupId());
            } catch (IllegalArgumentException e) {
                return OpResult.error(400, "service_group_id inválido");
            }
            if (dao.countServiceGroupInModule(serviceGroupId, moduleId) == 0)
                return OpResult.error(400, "Grupo não encontrado neste módulo");
        }

        String moduleSlug = dao.findModuleSlug(moduleId);
        String groupSlug = serviceGroupId != null ? dao.findGroupSlug(serviceGroupId) : null;
        String routeKey = generateRouteKey(moduleSlug, groupSlug, req.slug());

        if (dao.countServiceByRouteKeyExcluding(routeKey, id) > 0)
            return OpResult.error(400, "Já existe outro serviço com este Route Key: " + routeKey);

        int updated = dao.updateService(moduleId, id, req, serviceGroupId, routeKey);
        if (updated == 0) return OpResult.error(404, "Serviço não encontrado");
        return OpResult.ok(200, Map.of("ok", true));
    }

    @Override
    @Transactional
    public boolean toggleServiceStatus(String moduleId, String id) {
        return dao.toggleServiceStatus(moduleId, id) > 0;
    }

    // ─── Grupos de serviços ────────────────────────────────────────────────

    @Override
    public List<PlatformModuleServiceGroupDTO> listServiceGroups(String moduleId) {
        return dao.findServiceGroups(moduleId);
    }

    @Override
    @Transactional
    public OpResult createServiceGroup(String moduleId, ModuleServiceGroupRequest req) {
        if (req.name() == null || req.name().isBlank())
            return OpResult.error(400, "name é obrigatório");
        if (req.slug() == null || req.slug().isBlank())
            return OpResult.error(400, "slug é obrigatório");
        if (!isValidSlug(req.slug()))
            return OpResult.error(400, "Slug inválido. Use apenas letras minúsculas, números e hífen");

        if (dao.countModuleById(moduleId) == 0)
            return OpResult.error(404, "Módulo não encontrado");

        if (dao.countGroupBySlug(moduleId, req.slug()) > 0)
            return OpResult.error(400, "Já existe um grupo com este slug neste módulo");

        return OpResult.ok(201, dao.insertServiceGroup(moduleId, req));
    }

    @Override
    @Transactional
    public OpResult updateServiceGroup(String moduleId, String id, ModuleServiceGroupRequest req) {
        if (req.name() == null || req.name().isBlank())
            return OpResult.error(400, "name é obrigatório");
        if (req.slug() == null || req.slug().isBlank())
            return OpResult.error(400, "slug é obrigatório");
        if (!isValidSlug(req.slug()))
            return OpResult.error(400, "Slug inválido. Use apenas letras minúsculas, números e hífen");

        if (dao.countGroupBySlugExcluding(moduleId, req.slug(), id) > 0)
            return OpResult.error(400, "Já existe outro grupo com este slug neste módulo");

        int updated = dao.updateServiceGroup(moduleId, id, req);
        if (updated == 0) return OpResult.error(404, "Grupo não encontrado");
        return OpResult.ok(200, Map.of("ok", true));
    }

    @Override
    @Transactional
    public OpResult updateServiceGroupStatus(String moduleId, String id, String newStatus) {
        if ("INACTIVE".equals(newStatus)) {
            long activeCount = dao.countActiveServicesInGroup(id);
            if (activeCount > 0)
                return OpResult.error(409, "Este grupo possui " + activeCount + " serviço(s) ativo(s). Remova ou mova-os antes de inativar.");
        }

        int updated = dao.updateServiceGroupStatus(moduleId, id, newStatus);
        if (updated == 0) return OpResult.error(404, "Grupo não encontrado");
        return OpResult.ok(200, Map.of("ok", true, "status", newStatus));
    }
}
