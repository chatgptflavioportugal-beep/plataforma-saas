package com.saas.admin.dao;

import com.saas.admin.dto.ModuleRequest;
import com.saas.admin.dto.ModuleServiceGroupRequest;
import com.saas.admin.dto.ModuleServiceRequest;
import com.saas.admin.dto.PlatformModuleDTO;
import com.saas.admin.dto.PlatformModuleServiceDTO;
import com.saas.admin.dto.PlatformModuleServiceGroupDTO;
import com.saas.admin.to.PlatformModuleServiceGroupTO;
import com.saas.admin.to.PlatformModuleServiceTO;
import com.saas.admin.to.PlatformModuleTO;
import com.saas.platformdatabase.query.DatabaseQuery;
import com.saas.platformdatabase.query.NativeQuery;
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

    @Inject
    DatabaseQuery databaseQuery;

    // ─── Módulos ───────────────────────────────────────────────────────────

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

        NativeQuery<PlatformModuleTO> query = databaseQuery.nativeQuery(em, sql.toString(), PlatformModuleTO.class);
        params.forEach(query::setParameter);
        List<PlatformModuleTO> rows = query.getResultList();

        return rows.stream().map(row -> new PlatformModuleDTO(
            row.id(), row.name(), row.slug(), row.description(), row.moduleUrl(),
            row.iconPath(), row.isActive(), row.sortOrder(), row.createdAt(), row.updatedAt(),
            row.serviceCount()
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

    public PlatformModuleDTO insertModule(ModuleRequest req) {
        boolean isActive = req.isActive() == null || Boolean.TRUE.equals(req.isActive());
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : 99;

        PlatformModuleTO r = databaseQuery
                .nativeQuery(em, """
                        INSERT INTO platform_modules (name, slug, description, module_url, icon_path, is_active, sort_order)
                        VALUES (:name, :slug, :description, :moduleUrl, :iconPath, :isActive, :sortOrder)
                        RETURNING id::text, name, slug, description, module_url, icon_path, is_active, sort_order, created_at::text, updated_at::text
                        """, PlatformModuleTO.class)
                .setParameter("name", req.name())
                .setParameter("slug", req.slug())
                .setParameter("description", req.description())
                .setParameter("moduleUrl", req.moduleUrl())
                .setParameter("iconPath", req.iconPath())
                .setParameter("isActive", isActive)
                .setParameter("sortOrder", sortOrder)
                .getOptionalResult()
                .orElseThrow();

        return new PlatformModuleDTO(
            r.id(), r.name(), r.slug(), r.description(), r.moduleUrl(),
            r.iconPath(), r.isActive(), r.sortOrder(), r.createdAt(), r.updatedAt(), 0);
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

    public List<PlatformModuleServiceDTO> findServices(String moduleId) {
        List<PlatformModuleServiceTO> rows = databaseQuery
                .nativeQuery(em, """
                        SELECT s.id::text, s.module_id::text, s.name, s.slug, s.description,
                          s.icon_path, s.is_active, s.sort_order, s.created_at::text, s.updated_at::text,
                          s.service_group_id::text, g.name AS group_name, g.slug AS group_slug, s.route_key
                        FROM platform_module_services s
                        LEFT JOIN platform_module_service_groups g ON g.id = s.service_group_id
                        WHERE s.module_id::text = :moduleId
                        ORDER BY COALESCE(g.sort_order, 9999), s.sort_order, s.name
                        """, PlatformModuleServiceTO.class)
                .setParameter("moduleId", moduleId)
                .getResultList();

        return rows.stream().map(row -> new PlatformModuleServiceDTO(
            row.id(), row.moduleId(), row.name(), row.slug(), row.description(),
            row.iconPath(), row.isActive(), row.sortOrder(), row.createdAt(), row.updatedAt(),
            row.serviceGroupId(), row.groupName(), row.groupSlug(), row.routeKey()
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

    public PlatformModuleServiceDTO insertService(String moduleId, ModuleServiceRequest req, UUID serviceGroupId, String routeKey) {
        boolean isActive = req.isActive() == null || Boolean.TRUE.equals(req.isActive());
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : 99;

        String sql = "INSERT INTO platform_module_services " +
            "(module_id, service_group_id, name, slug, description, icon_path, is_active, sort_order, route_key) " +
            "VALUES (CAST(:moduleId AS uuid), " + (serviceGroupId != null ? "CAST(:serviceGroupId AS uuid)" : "NULL") + ", " +
            ":name, :slug, :description, :iconPath, :isActive, :sortOrder, :routeKey) " +
            "RETURNING id::text, module_id::text, name, slug, description, icon_path, is_active, sort_order, created_at::text, updated_at::text, service_group_id::text, route_key";

        NativeQuery<PlatformModuleServiceTO> query = databaseQuery.nativeQuery(em, sql, PlatformModuleServiceTO.class)
            .setParameter("moduleId", moduleId)
            .setParameter("name", req.name())
            .setParameter("slug", req.slug())
            .setParameter("description", req.description())
            .setParameter("iconPath", req.iconPath())
            .setParameter("isActive", isActive)
            .setParameter("sortOrder", sortOrder)
            .setParameter("routeKey", routeKey);
        if (serviceGroupId != null) query.setParameter("serviceGroupId", serviceGroupId.toString());

        PlatformModuleServiceTO r = query.getOptionalResult().orElseThrow();
        return new PlatformModuleServiceDTO(
            r.id(), r.moduleId(), r.name(), r.slug(), r.description(),
            r.iconPath(), r.isActive(), r.sortOrder(), r.createdAt(), r.updatedAt(),
            r.serviceGroupId(), null, null, r.routeKey());
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

    public List<PlatformModuleServiceGroupDTO> findServiceGroups(String moduleId) {
        List<PlatformModuleServiceGroupTO> rows = databaseQuery
                .nativeQuery(em, """
                        SELECT g.id::text, g.module_id::text, g.name, g.slug, g.description, g.icon_path,
                          g.sort_order, g.status, g.created_at::text, g.updated_at::text,
                          (SELECT COUNT(*) FROM platform_module_services s WHERE s.service_group_id = g.id)::int AS service_count
                        FROM platform_module_service_groups g
                        WHERE g.module_id::text = :moduleId
                        ORDER BY g.sort_order, g.name
                        """, PlatformModuleServiceGroupTO.class)
                .setParameter("moduleId", moduleId)
                .getResultList();

        return rows.stream().map(row -> new PlatformModuleServiceGroupDTO(
            row.id(), row.moduleId(), row.name(), row.slug(), row.description(),
            row.iconPath(), row.sortOrder(), row.status(), row.createdAt(), row.updatedAt(),
            row.serviceCount()
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

    public PlatformModuleServiceGroupDTO insertServiceGroup(String moduleId, ModuleServiceGroupRequest req) {
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : 99;
        String status = req.status() != null ? req.status() : "ACTIVE";

        PlatformModuleServiceGroupTO r = databaseQuery
                .nativeQuery(em, """
                        INSERT INTO platform_module_service_groups (module_id, name, slug, description, icon_path, sort_order, status)
                        VALUES (CAST(:moduleId AS uuid), :name, :slug, :description, :iconPath, :sortOrder, :status)
                        RETURNING id::text, module_id::text, name, slug, description, icon_path, sort_order, status, created_at::text, updated_at::text
                        """, PlatformModuleServiceGroupTO.class)
                .setParameter("moduleId", moduleId).setParameter("name", req.name().trim()).setParameter("slug", req.slug())
                .setParameter("description", req.description()).setParameter("iconPath", req.iconPath())
                .setParameter("sortOrder", sortOrder).setParameter("status", status)
                .getOptionalResult()
                .orElseThrow();

        return new PlatformModuleServiceGroupDTO(
            r.id(), r.moduleId(), r.name(), r.slug(), r.description(),
            r.iconPath(), r.sortOrder(), r.status(), r.createdAt(), r.updatedAt(), 0);
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
