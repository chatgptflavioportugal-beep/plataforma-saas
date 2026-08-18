package com.saas.admin.dao;

import com.saas.admin.dto.ModuleRequest;
import com.saas.admin.dto.ModuleServiceGroupRequest;
import com.saas.admin.dto.ModuleServiceRequest;
import com.saas.admin.dto.PlatformModuleDTO;
import com.saas.admin.dto.PlatformModuleServiceDTO;
import com.saas.admin.dto.PlatformModuleServiceGroupDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a dados do catálogo de módulos/serviços/grupos de serviços
 * (platform_modules / platform_module_services / platform_module_service_groups).
 */
@ApplicationScoped
public class ModuleCatalogDAO {

    @Inject
    EntityManager em;

    // ─── Módulos ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<PlatformModuleDTO> findModules(String search, Boolean isActive) {
        StringBuilder sql = new StringBuilder(
            "SELECT m.id::text, m.name, m.slug, m.description, m.module_url, m.icon_path, " +
            "  m.is_active, m.sort_order, m.created_at::text, m.updated_at::text, " +
            "  (SELECT COUNT(*) FROM platform_module_services s WHERE s.module_id = m.id)::int AS service_count " +
            "FROM platform_modules m WHERE 1=1"
        );
        Map<String, Object> params = new LinkedHashMap<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(m.name) LIKE LOWER(:search) OR LOWER(m.slug) LIKE LOWER(:search))");
            params.put("search", "%" + search.trim() + "%");
        }
        if (isActive != null) {
            sql.append(isActive ? " AND m.is_active = TRUE" : " AND m.is_active = FALSE");
        }
        sql.append(" ORDER BY m.sort_order, m.name");

        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(row -> new PlatformModuleDTO(
            (String) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4],
            (String) row[5], (Boolean) row[6], (Integer) row[7], (String) row[8], (String) row[9],
            (Integer) row[10]
        )).toList();
    }

    public long countModuleBySlug(String slug) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_modules WHERE slug = :slug"
        ).setParameter("slug", slug).getSingleResult()).longValue();
    }

    public long countModuleBySlugExcluding(String slug, String excludeId) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_modules WHERE slug = :slug AND id::text != :id"
        ).setParameter("slug", slug).setParameter("id", excludeId).getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    public PlatformModuleDTO insertModule(ModuleRequest req) {
        boolean isActive = req.isActive() == null || Boolean.TRUE.equals(req.isActive());
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : 99;

        List<Object[]> rows = em.createNativeQuery(
            "INSERT INTO platform_modules (name, slug, description, module_url, icon_path, is_active, sort_order) " +
            "VALUES (:name, :slug, :description, :moduleUrl, :iconPath, :isActive, :sortOrder) " +
            "RETURNING id::text, name, slug, description, module_url, icon_path, is_active, sort_order, created_at::text, updated_at::text"
        )
        .setParameter("name", req.name())
        .setParameter("slug", req.slug())
        .setParameter("description", req.description())
        .setParameter("moduleUrl", req.moduleUrl())
        .setParameter("iconPath", req.iconPath())
        .setParameter("isActive", isActive)
        .setParameter("sortOrder", sortOrder)
        .getResultList();

        Object[] r = rows.get(0);
        return new PlatformModuleDTO(
            (String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
            (String) r[5], (Boolean) r[6], (Integer) r[7], (String) r[8], (String) r[9], 0);
    }

    public int updateModule(String id, ModuleRequest req) {
        boolean isActive = req.isActive() == null || Boolean.TRUE.equals(req.isActive());
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : 99;

        return em.createNativeQuery(
            "UPDATE platform_modules SET name = :name, slug = :slug, description = :description, " +
            "module_url = :moduleUrl, icon_path = :iconPath, is_active = :isActive, sort_order = :sortOrder " +
            "WHERE id::text = :id"
        )
        .setParameter("name", req.name())
        .setParameter("slug", req.slug())
        .setParameter("description", req.description())
        .setParameter("moduleUrl", req.moduleUrl())
        .setParameter("iconPath", req.iconPath())
        .setParameter("isActive", isActive)
        .setParameter("sortOrder", sortOrder)
        .setParameter("id", id)
        .executeUpdate();
    }

    public int toggleModuleStatus(String id) {
        return em.createNativeQuery(
            "UPDATE platform_modules SET is_active = NOT is_active WHERE id::text = :id"
        ).setParameter("id", id).executeUpdate();
    }

    public long countModuleById(String id) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_modules WHERE id::text = :id"
        ).setParameter("id", id).getSingleResult()).longValue();
    }

    public String findModuleSlug(String id) {
        return (String) em.createNativeQuery(
            "SELECT slug FROM platform_modules WHERE id::text = :id"
        ).setParameter("id", id).getSingleResult();
    }

    // ─── Serviços ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<PlatformModuleServiceDTO> findServices(String moduleId) {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT s.id::text, s.module_id::text, s.name, s.slug, s.description, " +
            "  s.icon_path, s.is_active, s.sort_order, s.created_at::text, s.updated_at::text, " +
            "  s.service_group_id::text, g.name AS group_name, g.slug AS group_slug, s.route_key " +
            "FROM platform_module_services s " +
            "LEFT JOIN platform_module_service_groups g ON g.id = s.service_group_id " +
            "WHERE s.module_id::text = :moduleId " +
            "ORDER BY COALESCE(g.sort_order, 9999), s.sort_order, s.name"
        ).setParameter("moduleId", moduleId).getResultList();

        return rows.stream().map(row -> new PlatformModuleServiceDTO(
            (String) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4],
            (String) row[5], (Boolean) row[6], (Integer) row[7], (String) row[8], (String) row[9],
            (String) row[10], (String) row[11], (String) row[12], (String) row[13]
        )).toList();
    }

    public long countServiceBySlug(String moduleId, String slug) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_services WHERE module_id::text = :moduleId AND slug = :slug"
        ).setParameter("moduleId", moduleId).setParameter("slug", slug).getSingleResult()).longValue();
    }

    public long countServiceBySlugExcluding(String moduleId, String slug, String excludeId) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_services WHERE module_id::text = :moduleId AND slug = :slug AND id::text != :id"
        ).setParameter("moduleId", moduleId).setParameter("slug", slug).setParameter("id", excludeId).getSingleResult()).longValue();
    }

    public long countServiceGroupInModule(UUID groupId, String moduleId) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_service_groups WHERE id = CAST(:gid AS uuid) AND module_id::text = :moduleId"
        ).setParameter("gid", groupId.toString()).setParameter("moduleId", moduleId).getSingleResult()).longValue();
    }

    public String findGroupSlug(UUID groupId) {
        return (String) em.createNativeQuery(
            "SELECT slug FROM platform_module_service_groups WHERE id = CAST(:gid AS uuid)"
        ).setParameter("gid", groupId.toString()).getSingleResult();
    }

    public long countServiceByRouteKey(String routeKey) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_services WHERE route_key = :routeKey"
        ).setParameter("routeKey", routeKey).getSingleResult()).longValue();
    }

    public long countServiceByRouteKeyExcluding(String routeKey, String excludeId) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_services WHERE route_key = :routeKey AND id::text != :id"
        ).setParameter("routeKey", routeKey).setParameter("id", excludeId).getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    public PlatformModuleServiceDTO insertService(String moduleId, ModuleServiceRequest req, UUID serviceGroupId, String routeKey) {
        boolean isActive = req.isActive() == null || Boolean.TRUE.equals(req.isActive());
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : 99;

        String sql = "INSERT INTO platform_module_services " +
            "(module_id, service_group_id, name, slug, description, icon_path, is_active, sort_order, route_key) " +
            "VALUES (CAST(:moduleId AS uuid), " + (serviceGroupId != null ? "CAST(:serviceGroupId AS uuid)" : "NULL") + ", " +
            ":name, :slug, :description, :iconPath, :isActive, :sortOrder, :routeKey) " +
            "RETURNING id::text, module_id::text, name, slug, description, icon_path, is_active, sort_order, created_at::text, updated_at::text, service_group_id::text, route_key";

        Query query = em.createNativeQuery(sql)
            .setParameter("moduleId", moduleId)
            .setParameter("name", req.name())
            .setParameter("slug", req.slug())
            .setParameter("description", req.description())
            .setParameter("iconPath", req.iconPath())
            .setParameter("isActive", isActive)
            .setParameter("sortOrder", sortOrder)
            .setParameter("routeKey", routeKey);
        if (serviceGroupId != null) query.setParameter("serviceGroupId", serviceGroupId.toString());

        List<Object[]> rows = query.getResultList();
        Object[] r = rows.get(0);
        return new PlatformModuleServiceDTO(
            (String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
            (String) r[5], (Boolean) r[6], (Integer) r[7], (String) r[8], (String) r[9],
            (String) r[10], null, null, (String) r[11]);
    }

    public int updateService(String moduleId, String id, ModuleServiceRequest req, UUID serviceGroupId, String routeKey) {
        boolean isActive = req.isActive() == null || Boolean.TRUE.equals(req.isActive());
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : 99;

        String sql = "UPDATE platform_module_services SET name = :name, slug = :slug, description = :description, " +
            "icon_path = :iconPath, is_active = :isActive, sort_order = :sortOrder, " +
            "service_group_id = " + (serviceGroupId != null ? "CAST(:serviceGroupId AS uuid)" : "NULL") + ", route_key = :routeKey " +
            "WHERE id::text = :id AND module_id::text = :moduleId";

        Query query = em.createNativeQuery(sql)
            .setParameter("name", req.name())
            .setParameter("slug", req.slug())
            .setParameter("description", req.description())
            .setParameter("iconPath", req.iconPath())
            .setParameter("isActive", isActive)
            .setParameter("sortOrder", sortOrder)
            .setParameter("routeKey", routeKey)
            .setParameter("id", id)
            .setParameter("moduleId", moduleId);
        if (serviceGroupId != null) query.setParameter("serviceGroupId", serviceGroupId.toString());

        return query.executeUpdate();
    }

    public int toggleServiceStatus(String moduleId, String id) {
        return em.createNativeQuery(
            "UPDATE platform_module_services SET is_active = NOT is_active WHERE id::text = :id AND module_id::text = :moduleId"
        ).setParameter("id", id).setParameter("moduleId", moduleId).executeUpdate();
    }

    // ─── Grupos de serviços ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<PlatformModuleServiceGroupDTO> findServiceGroups(String moduleId) {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT g.id::text, g.module_id::text, g.name, g.slug, g.description, g.icon_path, " +
            "  g.sort_order, g.status, g.created_at::text, g.updated_at::text, " +
            "  (SELECT COUNT(*) FROM platform_module_services s WHERE s.service_group_id = g.id)::int " +
            "FROM platform_module_service_groups g " +
            "WHERE g.module_id::text = :moduleId " +
            "ORDER BY g.sort_order, g.name"
        ).setParameter("moduleId", moduleId).getResultList();

        return rows.stream().map(row -> new PlatformModuleServiceGroupDTO(
            (String) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4],
            (String) row[5], (Integer) row[6], (String) row[7], (String) row[8], (String) row[9],
            (Integer) row[10]
        )).toList();
    }

    public long countGroupBySlug(String moduleId, String slug) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_service_groups WHERE module_id::text = :moduleId AND slug = :slug"
        ).setParameter("moduleId", moduleId).setParameter("slug", slug).getSingleResult()).longValue();
    }

    public long countGroupBySlugExcluding(String moduleId, String slug, String excludeId) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_service_groups WHERE module_id::text = :moduleId AND slug = :slug AND id::text != :id"
        ).setParameter("moduleId", moduleId).setParameter("slug", slug).setParameter("id", excludeId).getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    public PlatformModuleServiceGroupDTO insertServiceGroup(String moduleId, ModuleServiceGroupRequest req) {
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : 99;
        String status = req.status() != null ? req.status() : "ACTIVE";

        List<Object[]> rows = em.createNativeQuery(
            "INSERT INTO platform_module_service_groups (module_id, name, slug, description, icon_path, sort_order, status) " +
            "VALUES (CAST(:moduleId AS uuid), :name, :slug, :description, :iconPath, :sortOrder, :status) " +
            "RETURNING id::text, module_id::text, name, slug, description, icon_path, sort_order, status, created_at::text, updated_at::text"
        )
        .setParameter("moduleId", moduleId).setParameter("name", req.name().trim()).setParameter("slug", req.slug())
        .setParameter("description", req.description()).setParameter("iconPath", req.iconPath())
        .setParameter("sortOrder", sortOrder).setParameter("status", status)
        .getResultList();

        Object[] r = rows.get(0);
        return new PlatformModuleServiceGroupDTO(
            (String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
            (String) r[5], (Integer) r[6], (String) r[7], (String) r[8], (String) r[9], 0);
    }

    public int updateServiceGroup(String moduleId, String id, ModuleServiceGroupRequest req) {
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : 99;

        return em.createNativeQuery(
            "UPDATE platform_module_service_groups SET name = :name, slug = :slug, description = :description, " +
            "icon_path = :iconPath, sort_order = :sortOrder " +
            "WHERE id::text = :id AND module_id::text = :moduleId"
        )
        .setParameter("name", req.name().trim()).setParameter("slug", req.slug())
        .setParameter("description", req.description()).setParameter("iconPath", req.iconPath())
        .setParameter("sortOrder", sortOrder)
        .setParameter("id", id).setParameter("moduleId", moduleId)
        .executeUpdate();
    }

    public long countActiveServicesInGroup(String groupId) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_services WHERE service_group_id::text = :id AND is_active = TRUE"
        ).setParameter("id", groupId).getSingleResult()).longValue();
    }

    public int updateServiceGroupStatus(String moduleId, String id, String status) {
        return em.createNativeQuery(
            "UPDATE platform_module_service_groups SET status = :status " +
            "WHERE id::text = :id AND module_id::text = :moduleId"
        ).setParameter("status", status).setParameter("id", id).setParameter("moduleId", moduleId).executeUpdate();
    }
}
