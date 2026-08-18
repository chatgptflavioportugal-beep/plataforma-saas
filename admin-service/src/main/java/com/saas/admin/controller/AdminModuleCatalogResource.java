package com.saas.admin.controller;

import com.saas.admin.dto.ModuleRequest;
import com.saas.admin.dto.ModuleServiceGroupRequest;
import com.saas.admin.dto.ModuleServiceRequest;
import com.saas.admin.dto.PlatformModuleDTO;
import com.saas.admin.dto.PlatformModuleServiceDTO;
import com.saas.admin.dto.PlatformModuleServiceGroupDTO;
import com.saas.admin.dto.UpdateStatusRequest;
import com.saas.admin.security.AdminAuthService;
import com.saas.admin.service.ModuleCatalogService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
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

    @Inject AdminAuthService adminAuth;
    @Inject ModuleCatalogService moduleCatalogService;

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
    public Response listModules(
            @Parameter(description = "Filtra por nome ou slug do módulo (busca parcial, case-insensitive).") @QueryParam("search") String search,
            @Parameter(description = "Filtra por status do módulo (true = ativos, false = inativos; omitido = todos).") @QueryParam("is_active") Boolean isActive) {
        adminAuth.requireAdminPermission("admin.modules.view");

        List<PlatformModuleDTO> modules = moduleCatalogService.listModules(search, isActive);
        return Response.ok(modules).build();
    }

    @POST
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
    public Response createModule(ModuleRequest req) {
        adminAuth.requireAdminPermission("admin.modules.create");

        ModuleCatalogService.OpResult result = moduleCatalogService.createModule(req);
        return Response.status(result.status()).entity(result.body()).build();
    }

    @PATCH
    @Path("/{id}")
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
            ModuleRequest req) {
        adminAuth.requireAdminPermission("admin.modules.edit");

        ModuleCatalogService.OpResult result = moduleCatalogService.updateModule(id, req);
        return Response.status(result.status()).entity(result.body()).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Consumes(MediaType.WILDCARD)
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

        boolean updated = moduleCatalogService.toggleModuleStatus(id);
        if (!updated) return Response.status(404).entity(Map.of("error", "Módulo não encontrado")).build();
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
    public Response listModuleServices(
            @Parameter(description = "ID (UUID) do módulo cujos serviços serão listados.", required = true) @PathParam("moduleId") String moduleId) {
        adminAuth.requireAdminPermission("admin.services.view");

        List<PlatformModuleServiceDTO> services = moduleCatalogService.listServices(moduleId);
        return Response.ok(services).build();
    }

    @POST
    @Path("/{moduleId}/services")
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
    public Response createModuleService(
            @Parameter(description = "ID (UUID) do módulo ao qual o serviço será adicionado.", required = true) @PathParam("moduleId") String moduleId,
            ModuleServiceRequest req) {
        adminAuth.requireAdminPermission("admin.services.create");

        ModuleCatalogService.OpResult result = moduleCatalogService.createService(moduleId, req);
        return Response.status(result.status()).entity(result.body()).build();
    }

    @PATCH
    @Path("/{moduleId}/services/{id}")
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
            ModuleServiceRequest req) {
        adminAuth.requireAdminPermission("admin.services.edit");

        ModuleCatalogService.OpResult result = moduleCatalogService.updateService(moduleId, id, req);
        return Response.status(result.status()).entity(result.body()).build();
    }

    @PATCH
    @Path("/{moduleId}/services/{id}/status")
    @Consumes(MediaType.WILDCARD)
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

        boolean updated = moduleCatalogService.toggleServiceStatus(moduleId, id);
        if (!updated) return Response.status(404).entity(Map.of("error", "Serviço não encontrado")).build();
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
    public Response listModuleServiceGroups(
            @Parameter(description = "ID (UUID) do módulo cujos grupos de serviços serão listados.", required = true) @PathParam("moduleId") String moduleId) {
        adminAuth.requireAdminPermission("admin.services.groups.view");

        List<PlatformModuleServiceGroupDTO> groups = moduleCatalogService.listServiceGroups(moduleId);
        return Response.ok(groups).build();
    }

    @POST
    @Path("/{moduleId}/service-groups")
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
    public Response createModuleServiceGroup(
            @Parameter(description = "ID (UUID) do módulo ao qual o grupo será adicionado.", required = true) @PathParam("moduleId") String moduleId,
            ModuleServiceGroupRequest req) {
        adminAuth.requireAdminPermission("admin.services.groups.create");

        ModuleCatalogService.OpResult result = moduleCatalogService.createServiceGroup(moduleId, req);
        return Response.status(result.status()).entity(result.body()).build();
    }

    @PATCH
    @Path("/{moduleId}/service-groups/{id}")
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
            ModuleServiceGroupRequest req) {
        adminAuth.requireAdminPermission("admin.services.groups.edit");

        ModuleCatalogService.OpResult result = moduleCatalogService.updateServiceGroup(moduleId, id, req);
        return Response.status(result.status()).entity(result.body()).build();
    }

    @PATCH
    @Path("/{moduleId}/service-groups/{id}/status")
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
            UpdateStatusRequest req) {
        String newStatus = req != null ? req.status() : null;
        if (!List.of("ACTIVE", "INACTIVE").contains(newStatus))
            return Response.status(400).entity(Map.of("error", "Status inválido. Use ACTIVE ou INACTIVE")).build();
        adminAuth.requireAdminPermission("ACTIVE".equals(newStatus) ? "admin.services.groups.activate" : "admin.services.groups.deactivate");

        ModuleCatalogService.OpResult result = moduleCatalogService.updateServiceGroupStatus(moduleId, id, newStatus);
        return Response.status(result.status()).entity(result.body()).build();
    }
}
