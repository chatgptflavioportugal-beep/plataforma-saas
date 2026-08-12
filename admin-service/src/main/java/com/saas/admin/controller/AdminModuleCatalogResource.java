package com.saas.admin.controller;

import com.saas.admin.security.AdminAuthService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * CRUD administrativo do catálogo de módulos/serviços/grupos de serviços da plataforma.
 *
 * Movido de module-catalog-service (que por sua vez havia herdado de AdminResource no
 * backend-quarkus) para consolidar toda escrita estrutural da plataforma em admin-service,
 * conforme o princípio de responsabilidade única — module-catalog-service permanece
 * exclusivamente leitura (ver ServiceRouteResource).
 */
@Path("/api/v1/admin/modules")
@Tag(name = "Modules", description = "CRUD administrativo do catálogo de módulos da plataforma (platform_modules). Contexto exclusivamente administrativo — não deve ser utilizado pelo ambiente cliente.")
@Tag(name = "Services", description = "CRUD administrativo dos serviços e grupos de serviços de cada módulo (platform_module_services / platform_module_service_groups). Contexto exclusivamente administrativo — não deve ser utilizado pelo ambiente cliente.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminModuleCatalogResource {

    @Inject EntityManager em;

    @Inject
    AdminAuthService adminAuth;

    private static final java.util.regex.Pattern SLUG_PATTERN =
        java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9_-]*$|^[a-z0-9]$");

    private boolean isValidSlug(String slug) {
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

    // ----------------------------------------------------------------
    // Módulos da plataforma
    // ----------------------------------------------------------------

    @GET
    @Operation(
        summary = "Lista os módulos cadastrados no catálogo da plataforma",
        description = "Operação exclusivamente administrativa (platform_modules) — não deve ser " +
            "utilizada pelo ambiente cliente. Lista todos os módulos com o total de serviços " +
            "cadastrados em cada um (`service_count`), filtráveis por nome/slug (`search`, " +
            "case-insensitive) e por status (`is_active`). Sem filtros, retorna todos os " +
            "módulos ordenados por `sort_order` e nome."
    )
    @APIResponse(responseCode = "200", description = "Lista de módulos (pode ser vazia).")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.modules.view`.")
    @SuppressWarnings("unchecked")
    public Response listModules(
            @Parameter(description = "Filtra por nome ou slug do módulo (busca parcial, case-insensitive).") @QueryParam("search") String search,
            @Parameter(description = "Filtra por status do módulo (true = ativos, false = inativos; omitido = todos).") @QueryParam("is_active") Boolean isActive) {
        adminAuth.requireAdminPermission("admin.modules.view");
        StringBuilder sql = new StringBuilder(
            "SELECT m.id::text, m.name, m.slug, m.description, m.module_url, m.icon_path, " +
            "  m.is_active, m.sort_order, m.created_at::text, m.updated_at::text, " +
            "  (SELECT COUNT(*) FROM platform_module_services s WHERE s.module_id = m.id)::int AS service_count " +
            "FROM platform_modules m WHERE 1=1"
        );
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(m.name) LIKE LOWER(:search) OR LOWER(m.slug) LIKE LOWER(:search))");
            params.put("search", "%" + search.trim() + "%");
        }
        if (isActive != null) {
            sql.append(isActive ? " AND m.is_active = TRUE" : " AND m.is_active = FALSE");
        }
        sql.append(" ORDER BY m.sort_order, m.name");
        var query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<Object[]> rows = (List<Object[]>) query.getResultList();
        var modules = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            m.put("slug", row[2]);
            m.put("description", row[3]);
            m.put("module_url", row[4]);
            m.put("icon_path", row[5]);
            m.put("is_active", row[6]);
            m.put("sort_order", row[7]);
            m.put("created_at", row[8]);
            m.put("updated_at", row[9]);
            m.put("service_count", row[10]);
            return m;
        }).toList();
        return Response.ok(modules).build();
    }

    @POST
    @Transactional
    @Operation(
        summary = "Cria um novo módulo no catálogo da plataforma",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Cria um registro em platform_modules com `name`, `slug` (único, " +
            "validado contra o padrão `^[a-z0-9][a-z0-9_-]*$`) e `module_url` obrigatórios; " +
            "`description`, `icon_path`, `is_active` (padrão true) e `sort_order` (padrão 99) " +
            "são opcionais."
    )
    @APIResponse(responseCode = "201", description = "Módulo criado com sucesso; retorna o registro completo, com `service_count = 0`.")
    @APIResponse(responseCode = "400", description = "`name`, `slug` ou `module_url` ausentes, `slug` em formato inválido, ou já existe um módulo com o `slug` informado.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.modules.create`.")
    public Response createModule(Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.modules.create");
        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        String moduleUrl = (String) body.get("module_url");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();
        if (slug == null || slug.isBlank())
            return Response.status(400).entity(Map.of("error", "slug é obrigatório")).build();
        if (!isValidSlug(slug))
            return Response.status(400).entity(Map.of("error", "Slug inválido. Use apenas letras minúsculas, números e hífen")).build();
        if (moduleUrl == null || moduleUrl.isBlank())
            return Response.status(400).entity(Map.of("error", "module_url é obrigatório")).build();

        long existing = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_modules WHERE slug = :slug"
        ).setParameter("slug", slug).getSingleResult()).longValue();
        if (existing > 0)
            return Response.status(400).entity(Map.of("error", "Já existe um módulo com este slug")).build();

        String description = (String) body.get("description");
        String iconPath = (String) body.get("icon_path");
        boolean isActive = body.get("is_active") == null || Boolean.TRUE.equals(body.get("is_active"));
        int sortOrder = body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "INSERT INTO platform_modules (name, slug, description, module_url, icon_path, is_active, sort_order) " +
            "VALUES (:name, :slug, :description, :moduleUrl, :iconPath, :isActive, :sortOrder) " +
            "RETURNING id::text, name, slug, description, module_url, icon_path, is_active, sort_order, created_at::text, updated_at::text"
        )
        .setParameter("name", name)
        .setParameter("slug", slug)
        .setParameter("description", description)
        .setParameter("moduleUrl", moduleUrl)
        .setParameter("iconPath", iconPath)
        .setParameter("isActive", isActive)
        .setParameter("sortOrder", sortOrder)
        .getResultList();

        Object[] r = rows.get(0);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", r[0]); result.put("name", r[1]); result.put("slug", r[2]);
        result.put("description", r[3]); result.put("module_url", r[4]); result.put("icon_path", r[5]);
        result.put("is_active", r[6]); result.put("sort_order", r[7]);
        result.put("created_at", r[8]); result.put("updated_at", r[9]);
        result.put("service_count", 0);
        return Response.status(201).entity(result).build();
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    @Operation(
        summary = "Atualiza os dados de um módulo do catálogo",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Substitui `name`, `slug`, `description`, `module_url`, " +
            "`icon_path`, `is_active` e `sort_order` do módulo (não é um PATCH parcial: `name`, " +
            "`slug` e `module_url` são obrigatórios no corpo, e os demais campos assumem " +
            "padrão quando omitidos, da mesma forma que na criação)."
    )
    @APIResponse(responseCode = "200", description = "Módulo atualizado com sucesso.")
    @APIResponse(responseCode = "400", description = "`name`, `slug` ou `module_url` ausentes, `slug` em formato inválido, ou já existe outro módulo com o `slug` informado.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.modules.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhum módulo encontrado para o `id` informado.")
    public Response updateModule(
            @Parameter(description = "ID (UUID) do módulo a atualizar.", required = true) @PathParam("id") String id,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.modules.edit");
        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        String moduleUrl = (String) body.get("module_url");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();
        if (slug == null || slug.isBlank())
            return Response.status(400).entity(Map.of("error", "slug é obrigatório")).build();
        if (!isValidSlug(slug))
            return Response.status(400).entity(Map.of("error", "Slug inválido. Use apenas letras minúsculas, números e hífen")).build();
        if (moduleUrl == null || moduleUrl.isBlank())
            return Response.status(400).entity(Map.of("error", "module_url é obrigatório")).build();

        long existing = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_modules WHERE slug = :slug AND id::text != :id"
        ).setParameter("slug", slug).setParameter("id", id).getSingleResult()).longValue();
        if (existing > 0)
            return Response.status(400).entity(Map.of("error", "Já existe outro módulo com este slug")).build();

        int updated = em.createNativeQuery(
            "UPDATE platform_modules SET name = :name, slug = :slug, description = :description, " +
            "module_url = :moduleUrl, icon_path = :iconPath, is_active = :isActive, sort_order = :sortOrder " +
            "WHERE id::text = :id"
        )
        .setParameter("name", name)
        .setParameter("slug", slug)
        .setParameter("description", body.get("description"))
        .setParameter("moduleUrl", moduleUrl)
        .setParameter("iconPath", body.get("icon_path"))
        .setParameter("isActive", body.get("is_active") == null || Boolean.TRUE.equals(body.get("is_active")))
        .setParameter("sortOrder", body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99)
        .setParameter("id", id)
        .executeUpdate();

        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Módulo não encontrado")).build();

        return Response.ok(Map.of("ok", true)).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    @Operation(
        summary = "Ativa ou inativa um módulo (alterna o status atual)",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Não recebe corpo: inverte diretamente `is_active` do módulo " +
            "(ativo vira inativo e vice-versa). Não valida efeitos em cascata sobre serviços " +
            "ou assinaturas dependentes deste módulo."
    )
    @APIResponse(responseCode = "200", description = "Status do módulo alternado com sucesso.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.modules.activate`.")
    @APIResponse(responseCode = "404", description = "Nenhum módulo encontrado para o `id` informado.")
    public Response toggleModuleStatus(
            @Parameter(description = "ID (UUID) do módulo a ativar/inativar.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.modules.activate");
        int updated = em.createNativeQuery(
            "UPDATE platform_modules SET is_active = NOT is_active WHERE id::text = :id"
        ).setParameter("id", id).executeUpdate();
        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Módulo não encontrado")).build();
        return Response.ok(Map.of("ok", true)).build();
    }

    // ----------------------------------------------------------------
    // Serviços/Itens dos módulos
    // ----------------------------------------------------------------

    @GET
    @Path("/{moduleId}/services")
    @Operation(
        summary = "Lista os serviços de um módulo do catálogo",
        description = "Operação exclusivamente administrativa (platform_module_services) — não " +
            "deve ser utilizada pelo ambiente cliente. Lista todos os serviços do módulo " +
            "informado, incluindo o grupo de serviços ao qual cada um pertence (quando " +
            "houver) e o `route_key` usado para resolução de rota no frontend. Não valida se " +
            "o módulo existe — para um `moduleId` inexistente, retorna lista vazia."
    )
    @APIResponse(responseCode = "200", description = "Lista de serviços do módulo (pode ser vazia).")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.services.view`.")
    @SuppressWarnings("unchecked")
    public Response listModuleServices(
            @Parameter(description = "ID (UUID) do módulo cujos serviços serão listados.", required = true) @PathParam("moduleId") String moduleId) {
        adminAuth.requireAdminPermission("admin.services.view");
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "SELECT s.id::text, s.module_id::text, s.name, s.slug, s.description, " +
            "  s.icon_path, s.is_active, s.sort_order, s.created_at::text, s.updated_at::text, " +
            "  s.service_group_id::text, g.name AS group_name, g.slug AS group_slug, s.route_key " +
            "FROM platform_module_services s " +
            "LEFT JOIN platform_module_service_groups g ON g.id = s.service_group_id " +
            "WHERE s.module_id::text = :moduleId " +
            "ORDER BY COALESCE(g.sort_order, 9999), s.sort_order, s.name"
        ).setParameter("moduleId", moduleId).getResultList();
        var services = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]); m.put("module_id", row[1]); m.put("name", row[2]);
            m.put("slug", row[3]); m.put("description", row[4]);
            m.put("icon_path", row[5]); m.put("is_active", row[6]); m.put("sort_order", row[7]);
            m.put("created_at", row[8]); m.put("updated_at", row[9]);
            m.put("service_group_id", row[10]); m.put("service_group_name", row[11]);
            m.put("service_group_slug", row[12]); m.put("route_key", row[13]);
            return m;
        }).toList();
        return Response.ok(services).build();
    }

    @POST
    @Path("/{moduleId}/services")
    @Transactional
    @Operation(
        summary = "Cria um novo serviço dentro de um módulo do catálogo",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Cria um registro em platform_module_services com `name` e " +
            "`slug` (único dentro do módulo) obrigatórios; `service_group_id` é opcional (o " +
            "grupo, se informado, deve pertencer ao mesmo módulo). O `route_key` é gerado " +
            "automaticamente a partir dos slugs do módulo, grupo (se houver) e serviço, e deve " +
            "ser globalmente único na tabela."
    )
    @APIResponse(responseCode = "201", description = "Serviço criado com sucesso; retorna o registro completo (`service_group_name` sempre retorna `null` nesta resposta).")
    @APIResponse(responseCode = "400", description = "`name` ou `slug` ausentes, `slug` em formato inválido, `service_group_id` inválido (não é UUID) ou não pertence a este módulo, já existe um serviço com este slug neste módulo, ou o `route_key` gerado já está em uso.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.services.create`.")
    @APIResponse(responseCode = "404", description = "Módulo não encontrado para o `moduleId` informado.")
    @SuppressWarnings("unchecked")
    public Response createModuleService(
            @Parameter(description = "ID (UUID) do módulo ao qual o serviço será adicionado.", required = true) @PathParam("moduleId") String moduleId,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.services.create");
        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();
        if (slug == null || slug.isBlank())
            return Response.status(400).entity(Map.of("error", "slug é obrigatório")).build();
        if (!isValidSlug(slug))
            return Response.status(400).entity(Map.of("error", "Slug inválido. Use apenas letras minúsculas, números e hífen")).build();

        long moduleExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_modules WHERE id::text = :id"
        ).setParameter("id", moduleId).getSingleResult()).longValue();
        if (moduleExists == 0)
            return Response.status(404).entity(Map.of("error", "Módulo não encontrado")).build();

        long slugExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_services WHERE module_id::text = :moduleId AND slug = :slug"
        ).setParameter("moduleId", moduleId).setParameter("slug", slug).getSingleResult()).longValue();
        if (slugExists > 0)
            return Response.status(400).entity(Map.of("error", "Já existe um serviço com este slug neste módulo")).build();

        String serviceGroupIdStr = (String) body.get("service_group_id");
        java.util.UUID serviceGroupId = null;
        if (serviceGroupIdStr != null && !serviceGroupIdStr.isBlank()) {
            try { serviceGroupId = java.util.UUID.fromString(serviceGroupIdStr); }
            catch (IllegalArgumentException e) {
                return Response.status(400).entity(Map.of("error", "service_group_id inválido")).build();
            }
            long groupExists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM platform_module_service_groups WHERE id = CAST(:gid AS uuid) AND module_id::text = :moduleId"
            ).setParameter("gid", serviceGroupId.toString()).setParameter("moduleId", moduleId).getSingleResult()).longValue();
            if (groupExists == 0)
                return Response.status(400).entity(Map.of("error", "Grupo não encontrado neste módulo")).build();
        }

        boolean isActive = body.get("is_active") == null || Boolean.TRUE.equals(body.get("is_active"));
        int sortOrder = body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99;

        String moduleSlug = (String) em.createNativeQuery(
            "SELECT slug FROM platform_modules WHERE id::text = :id"
        ).setParameter("id", moduleId).getSingleResult();

        String groupSlug = null;
        final java.util.UUID finalGroupId = serviceGroupId;
        if (finalGroupId != null) {
            groupSlug = (String) em.createNativeQuery(
                "SELECT slug FROM platform_module_service_groups WHERE id = CAST(:gid AS uuid)"
            ).setParameter("gid", finalGroupId.toString()).getSingleResult();
        }

        String routeKey = generateRouteKey(moduleSlug, groupSlug, slug);

        long routeKeyExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_services WHERE route_key = :routeKey"
        ).setParameter("routeKey", routeKey).getSingleResult()).longValue();
        if (routeKeyExists > 0)
            return Response.status(400).entity(Map.of("error", "Já existe um serviço com este Route Key: " + routeKey)).build();

        List<Object[]> rows;
        if (finalGroupId != null) {
            rows = (List<Object[]>) em.createNativeQuery(
                "INSERT INTO platform_module_services (module_id, service_group_id, name, slug, description, icon_path, is_active, sort_order, route_key) " +
                "VALUES (CAST(:moduleId AS uuid), CAST(:serviceGroupId AS uuid), :name, :slug, :description, :iconPath, :isActive, :sortOrder, :routeKey) " +
                "RETURNING id::text, module_id::text, name, slug, description, icon_path, is_active, sort_order, created_at::text, updated_at::text, service_group_id::text, route_key"
            )
            .setParameter("moduleId", moduleId).setParameter("serviceGroupId", finalGroupId.toString())
            .setParameter("name", name).setParameter("slug", slug)
            .setParameter("description", body.get("description")).setParameter("iconPath", body.get("icon_path"))
            .setParameter("isActive", isActive).setParameter("sortOrder", sortOrder).setParameter("routeKey", routeKey)
            .getResultList();
        } else {
            rows = (List<Object[]>) em.createNativeQuery(
                "INSERT INTO platform_module_services (module_id, service_group_id, name, slug, description, icon_path, is_active, sort_order, route_key) " +
                "VALUES (CAST(:moduleId AS uuid), NULL, :name, :slug, :description, :iconPath, :isActive, :sortOrder, :routeKey) " +
                "RETURNING id::text, module_id::text, name, slug, description, icon_path, is_active, sort_order, created_at::text, updated_at::text, service_group_id::text, route_key"
            )
            .setParameter("moduleId", moduleId).setParameter("name", name).setParameter("slug", slug)
            .setParameter("description", body.get("description")).setParameter("iconPath", body.get("icon_path"))
            .setParameter("isActive", isActive).setParameter("sortOrder", sortOrder).setParameter("routeKey", routeKey)
            .getResultList();
        }

        Object[] r = rows.get(0);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", r[0]); result.put("module_id", r[1]); result.put("name", r[2]);
        result.put("slug", r[3]); result.put("description", r[4]);
        result.put("icon_path", r[5]); result.put("is_active", r[6]); result.put("sort_order", r[7]);
        result.put("created_at", r[8]); result.put("updated_at", r[9]);
        result.put("service_group_id", r[10]); result.put("service_group_name", null);
        result.put("route_key", r[11]);
        return Response.status(201).entity(result).build();
    }

    @PATCH
    @Path("/{moduleId}/services/{id}")
    @Transactional
    @Operation(
        summary = "Atualiza os dados de um serviço de um módulo",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Substitui `name`, `slug`, `description`, `icon_path`, " +
            "`is_active`, `sort_order` e `service_group_id` do serviço (não é um PATCH " +
            "parcial: `name` e `slug` são obrigatórios). O `route_key` é recalculado a partir " +
            "dos slugs atuais de módulo/grupo/serviço e deve permanecer único na tabela. Não " +
            "valida explicitamente que o módulo do path (`moduleId`) existe — apenas que o " +
            "serviço (`id`) pertence a ele."
    )
    @APIResponse(responseCode = "200", description = "Serviço atualizado com sucesso.")
    @APIResponse(responseCode = "400", description = "`name` ou `slug` ausentes, `slug` em formato inválido, `service_group_id` inválido (não é UUID) ou não pertence a este módulo, já existe outro serviço com este slug neste módulo, ou o `route_key` recalculado já está em uso por outro serviço.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.services.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhum serviço encontrado para o `id` informado dentro do `moduleId` informado.")
    public Response updateModuleService(
            @Parameter(description = "ID (UUID) do módulo ao qual o serviço pertence.", required = true) @PathParam("moduleId") String moduleId,
            @Parameter(description = "ID (UUID) do serviço a atualizar.", required = true) @PathParam("id") String id,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.services.edit");
        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();
        if (slug == null || slug.isBlank())
            return Response.status(400).entity(Map.of("error", "slug é obrigatório")).build();
        if (!isValidSlug(slug))
            return Response.status(400).entity(Map.of("error", "Slug inválido. Use apenas letras minúsculas, números e hífen")).build();

        long slugExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_services WHERE module_id::text = :moduleId AND slug = :slug AND id::text != :id"
        ).setParameter("moduleId", moduleId).setParameter("slug", slug).setParameter("id", id).getSingleResult()).longValue();
        if (slugExists > 0)
            return Response.status(400).entity(Map.of("error", "Já existe outro serviço com este slug neste módulo")).build();

        String serviceGroupIdStr = (String) body.get("service_group_id");
        java.util.UUID serviceGroupId = null;
        if (serviceGroupIdStr != null && !serviceGroupIdStr.isBlank()) {
            try { serviceGroupId = java.util.UUID.fromString(serviceGroupIdStr); }
            catch (IllegalArgumentException e) {
                return Response.status(400).entity(Map.of("error", "service_group_id inválido")).build();
            }
            long groupExists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM platform_module_service_groups WHERE id = CAST(:gid AS uuid) AND module_id::text = :moduleId"
            ).setParameter("gid", serviceGroupId.toString()).setParameter("moduleId", moduleId).getSingleResult()).longValue();
            if (groupExists == 0)
                return Response.status(400).entity(Map.of("error", "Grupo não encontrado neste módulo")).build();
        }

        String moduleSlug = (String) em.createNativeQuery(
            "SELECT slug FROM platform_modules WHERE id::text = :id"
        ).setParameter("id", moduleId).getSingleResult();

        String groupSlug = null;
        if (serviceGroupId != null) {
            groupSlug = (String) em.createNativeQuery(
                "SELECT slug FROM platform_module_service_groups WHERE id = CAST(:gid AS uuid)"
            ).setParameter("gid", serviceGroupId.toString()).getSingleResult();
        }

        String routeKey = generateRouteKey(moduleSlug, groupSlug, slug);

        long routeKeyExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_services WHERE route_key = :routeKey AND id::text != :id"
        ).setParameter("routeKey", routeKey).setParameter("id", id).getSingleResult()).longValue();
        if (routeKeyExists > 0)
            return Response.status(400).entity(Map.of("error", "Já existe outro serviço com este Route Key: " + routeKey)).build();

        int updated;
        if (serviceGroupId != null) {
            updated = em.createNativeQuery(
                "UPDATE platform_module_services SET name = :name, slug = :slug, description = :description, " +
                "icon_path = :iconPath, is_active = :isActive, sort_order = :sortOrder, " +
                "service_group_id = CAST(:serviceGroupId AS uuid), route_key = :routeKey " +
                "WHERE id::text = :id AND module_id::text = :moduleId"
            )
            .setParameter("name", name).setParameter("slug", slug)
            .setParameter("description", body.get("description")).setParameter("iconPath", body.get("icon_path"))
            .setParameter("isActive", body.get("is_active") == null || Boolean.TRUE.equals(body.get("is_active")))
            .setParameter("sortOrder", body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99)
            .setParameter("serviceGroupId", serviceGroupId.toString()).setParameter("routeKey", routeKey)
            .setParameter("id", id).setParameter("moduleId", moduleId)
            .executeUpdate();
        } else {
            updated = em.createNativeQuery(
                "UPDATE platform_module_services SET name = :name, slug = :slug, description = :description, " +
                "icon_path = :iconPath, is_active = :isActive, sort_order = :sortOrder, " +
                "service_group_id = NULL, route_key = :routeKey " +
                "WHERE id::text = :id AND module_id::text = :moduleId"
            )
            .setParameter("name", name).setParameter("slug", slug)
            .setParameter("description", body.get("description")).setParameter("iconPath", body.get("icon_path"))
            .setParameter("isActive", body.get("is_active") == null || Boolean.TRUE.equals(body.get("is_active")))
            .setParameter("sortOrder", body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99)
            .setParameter("routeKey", routeKey)
            .setParameter("id", id).setParameter("moduleId", moduleId)
            .executeUpdate();
        }

        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Serviço não encontrado")).build();

        return Response.ok(Map.of("ok", true)).build();
    }

    @PATCH
    @Path("/{moduleId}/services/{id}/status")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    @Operation(
        summary = "Ativa ou inativa um serviço de um módulo (alterna o status atual)",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Não recebe corpo: inverte diretamente `is_active` do serviço " +
            "(ativo vira inativo e vice-versa), escopado ao `moduleId` informado."
    )
    @APIResponse(responseCode = "200", description = "Status do serviço alternado com sucesso.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.services.activate`.")
    @APIResponse(responseCode = "404", description = "Nenhum serviço encontrado para o `id` informado dentro do `moduleId` informado.")
    public Response toggleModuleServiceStatus(
            @Parameter(description = "ID (UUID) do módulo ao qual o serviço pertence.", required = true) @PathParam("moduleId") String moduleId,
            @Parameter(description = "ID (UUID) do serviço a ativar/inativar.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.services.activate");
        int updated = em.createNativeQuery(
            "UPDATE platform_module_services SET is_active = NOT is_active WHERE id::text = :id AND module_id::text = :moduleId"
        ).setParameter("id", id).setParameter("moduleId", moduleId).executeUpdate();
        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Serviço não encontrado")).build();
        return Response.ok(Map.of("ok", true)).build();
    }

    // ----------------------------------------------------------------
    // Grupos de Serviços dos módulos
    // ----------------------------------------------------------------

    @GET
    @Path("/{moduleId}/service-groups")
    @Operation(
        summary = "Lista os grupos de serviços de um módulo",
        description = "Operação exclusivamente administrativa (platform_module_service_groups) " +
            "— não deve ser utilizada pelo ambiente cliente. Lista os grupos de serviços do " +
            "módulo informado, cada um com o total de serviços vinculados (`service_count`). " +
            "Não valida se o módulo existe — para um `moduleId` inexistente, retorna lista vazia."
    )
    @APIResponse(responseCode = "200", description = "Lista de grupos de serviços do módulo (pode ser vazia).")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.services.groups.view`.")
    @SuppressWarnings("unchecked")
    public Response listModuleServiceGroups(
            @Parameter(description = "ID (UUID) do módulo cujos grupos de serviços serão listados.", required = true) @PathParam("moduleId") String moduleId) {
        adminAuth.requireAdminPermission("admin.services.groups.view");
        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "SELECT g.id::text, g.module_id::text, g.name, g.slug, g.description, g.icon_path, " +
            "  g.sort_order, g.status, g.created_at::text, g.updated_at::text, " +
            "  (SELECT COUNT(*) FROM platform_module_services s WHERE s.service_group_id = g.id)::int " +
            "FROM platform_module_service_groups g " +
            "WHERE g.module_id::text = :moduleId " +
            "ORDER BY g.sort_order, g.name"
        ).setParameter("moduleId", moduleId).getResultList();
        var groups = rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]); m.put("module_id", row[1]); m.put("name", row[2]);
            m.put("slug", row[3]); m.put("description", row[4]); m.put("icon_path", row[5]);
            m.put("sort_order", row[6]); m.put("status", row[7]);
            m.put("created_at", row[8]); m.put("updated_at", row[9]);
            m.put("service_count", row[10]);
            return m;
        }).toList();
        return Response.ok(groups).build();
    }

    @POST
    @Path("/{moduleId}/service-groups")
    @Transactional
    @Operation(
        summary = "Cria um novo grupo de serviços dentro de um módulo",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Cria um registro em platform_module_service_groups com `name` " +
            "e `slug` (único dentro do módulo) obrigatórios; `description`, `icon_path` e " +
            "`sort_order` (padrão 99) são opcionais, e `status` assume `ACTIVE` quando omitido."
    )
    @APIResponse(responseCode = "201", description = "Grupo de serviços criado com sucesso; retorna o registro completo, com `service_count = 0`.")
    @APIResponse(responseCode = "400", description = "`name` ou `slug` ausentes, `slug` em formato inválido, ou já existe um grupo com este slug neste módulo.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.services.groups.create`.")
    @APIResponse(responseCode = "404", description = "Módulo não encontrado para o `moduleId` informado.")
    @SuppressWarnings("unchecked")
    public Response createModuleServiceGroup(
            @Parameter(description = "ID (UUID) do módulo ao qual o grupo será adicionado.", required = true) @PathParam("moduleId") String moduleId,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.services.groups.create");
        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();
        if (slug == null || slug.isBlank())
            return Response.status(400).entity(Map.of("error", "slug é obrigatório")).build();
        if (!isValidSlug(slug))
            return Response.status(400).entity(Map.of("error", "Slug inválido. Use apenas letras minúsculas, números e hífen")).build();

        long moduleExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_modules WHERE id::text = :id"
        ).setParameter("id", moduleId).getSingleResult()).longValue();
        if (moduleExists == 0)
            return Response.status(404).entity(Map.of("error", "Módulo não encontrado")).build();

        long slugExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_service_groups WHERE module_id::text = :moduleId AND slug = :slug"
        ).setParameter("moduleId", moduleId).setParameter("slug", slug).getSingleResult()).longValue();
        if (slugExists > 0)
            return Response.status(400).entity(Map.of("error", "Já existe um grupo com este slug neste módulo")).build();

        int sortOrder = body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99;
        String status = body.get("status") instanceof String s ? s : "ACTIVE";

        List<Object[]> rows = (List<Object[]>) em.createNativeQuery(
            "INSERT INTO platform_module_service_groups (module_id, name, slug, description, icon_path, sort_order, status) " +
            "VALUES (CAST(:moduleId AS uuid), :name, :slug, :description, :iconPath, :sortOrder, :status) " +
            "RETURNING id::text, module_id::text, name, slug, description, icon_path, sort_order, status, created_at::text, updated_at::text"
        )
        .setParameter("moduleId", moduleId).setParameter("name", name.trim()).setParameter("slug", slug)
        .setParameter("description", body.get("description")).setParameter("iconPath", body.get("icon_path"))
        .setParameter("sortOrder", sortOrder).setParameter("status", status)
        .getResultList();

        Object[] r = rows.get(0);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", r[0]); result.put("module_id", r[1]); result.put("name", r[2]);
        result.put("slug", r[3]); result.put("description", r[4]); result.put("icon_path", r[5]);
        result.put("sort_order", r[6]); result.put("status", r[7]);
        result.put("created_at", r[8]); result.put("updated_at", r[9]);
        result.put("service_count", 0);
        return Response.status(201).entity(result).build();
    }

    @PATCH
    @Path("/{moduleId}/service-groups/{id}")
    @Transactional
    @Operation(
        summary = "Atualiza os dados de um grupo de serviços",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Substitui `name`, `slug`, `description`, `icon_path` e " +
            "`sort_order` do grupo (não é um PATCH parcial: `name` e `slug` são obrigatórios). " +
            "O `status` do grupo não é alterado por este endpoint — ver PATCH " +
            "`/{moduleId}/service-groups/{id}/status`."
    )
    @APIResponse(responseCode = "200", description = "Grupo de serviços atualizado com sucesso.")
    @APIResponse(responseCode = "400", description = "`name` ou `slug` ausentes, `slug` em formato inválido, ou já existe outro grupo com este slug neste módulo.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.services.groups.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhum grupo encontrado para o `id` informado dentro do `moduleId` informado.")
    public Response updateModuleServiceGroup(
            @Parameter(description = "ID (UUID) do módulo ao qual o grupo pertence.", required = true) @PathParam("moduleId") String moduleId,
            @Parameter(description = "ID (UUID) do grupo de serviços a atualizar.", required = true) @PathParam("id") String id,
            Map<String, Object> body) {
        adminAuth.requireAdminPermission("admin.services.groups.edit");
        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();
        if (slug == null || slug.isBlank())
            return Response.status(400).entity(Map.of("error", "slug é obrigatório")).build();
        if (!isValidSlug(slug))
            return Response.status(400).entity(Map.of("error", "Slug inválido. Use apenas letras minúsculas, números e hífen")).build();

        long slugExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM platform_module_service_groups WHERE module_id::text = :moduleId AND slug = :slug AND id::text != :id"
        ).setParameter("moduleId", moduleId).setParameter("slug", slug).setParameter("id", id).getSingleResult()).longValue();
        if (slugExists > 0)
            return Response.status(400).entity(Map.of("error", "Já existe outro grupo com este slug neste módulo")).build();

        int updated = em.createNativeQuery(
            "UPDATE platform_module_service_groups SET name = :name, slug = :slug, description = :description, " +
            "icon_path = :iconPath, sort_order = :sortOrder " +
            "WHERE id::text = :id AND module_id::text = :moduleId"
        )
        .setParameter("name", name.trim()).setParameter("slug", slug)
        .setParameter("description", body.get("description")).setParameter("iconPath", body.get("icon_path"))
        .setParameter("sortOrder", body.get("sort_order") != null ? ((Number) body.get("sort_order")).intValue() : 99)
        .setParameter("id", id).setParameter("moduleId", moduleId)
        .executeUpdate();

        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Grupo não encontrado")).build();

        return Response.ok(Map.of("ok", true)).build();
    }

    @PATCH
    @Path("/{moduleId}/service-groups/{id}/status")
    @Transactional
    @Operation(
        summary = "Ativa ou inativa um grupo de serviços, definindo o status explicitamente",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Diferente do toggle de módulo/serviço, aqui o novo `status` " +
            "(`ACTIVE` ou `INACTIVE`) é informado explicitamente no corpo da requisição. A " +
            "permissão exigida varia conforme a ação: `admin.services.groups.activate` para " +
            "`ACTIVE` ou `admin.services.groups.deactivate` para `INACTIVE`. Inativar um grupo " +
            "que ainda possui serviços ativos vinculados é bloqueado — os serviços precisam " +
            "ser removidos ou movidos para outro grupo antes."
    )
    @APIResponse(responseCode = "200", description = "Status do grupo atualizado com sucesso; retorna o novo `status`.")
    @APIResponse(responseCode = "400", description = "`status` ausente ou diferente de `ACTIVE`/`INACTIVE`.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.services.groups.activate`/`admin.services.groups.deactivate` correspondente à ação solicitada.")
    @APIResponse(responseCode = "404", description = "Nenhum grupo encontrado para o `id` informado dentro do `moduleId` informado.")
    @APIResponse(responseCode = "409", description = "Tentativa de inativar (`INACTIVE`) um grupo que ainda possui um ou mais serviços ativos vinculados.")
    public Response toggleModuleServiceGroupStatus(
            @Parameter(description = "ID (UUID) do módulo ao qual o grupo pertence.", required = true) @PathParam("moduleId") String moduleId,
            @Parameter(description = "ID (UUID) do grupo de serviços a ativar/inativar.", required = true) @PathParam("id") String id,
            Map<String, Object> body) {
        String newStatus = body instanceof Map<?,?> ? (String) body.get("status") : null;
        if (!List.of("ACTIVE", "INACTIVE").contains(newStatus))
            return Response.status(400).entity(Map.of("error", "Status inválido. Use ACTIVE ou INACTIVE")).build();
        adminAuth.requireAdminPermission("ACTIVE".equals(newStatus) ? "admin.services.groups.activate" : "admin.services.groups.deactivate");

        if ("INACTIVE".equals(newStatus)) {
            long activeCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM platform_module_services WHERE service_group_id::text = :id AND is_active = TRUE"
            ).setParameter("id", id).getSingleResult()).longValue();
            if (activeCount > 0)
                return Response.status(409).entity(Map.of(
                    "error", "Este grupo possui " + activeCount + " serviço(s) ativo(s). Remova ou mova-os antes de inativar.",
                    "active_service_count", activeCount
                )).build();
        }

        int updated = em.createNativeQuery(
            "UPDATE platform_module_service_groups SET status = :status " +
            "WHERE id::text = :id AND module_id::text = :moduleId"
        ).setParameter("status", newStatus).setParameter("id", id).setParameter("moduleId", moduleId).executeUpdate();
        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Grupo não encontrado")).build();
        return Response.ok(Map.of("ok", true, "status", newStatus)).build();
    }
}
