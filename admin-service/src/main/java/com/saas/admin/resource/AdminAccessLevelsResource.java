package com.saas.admin.resource;

import com.saas.admin.dto.AccessLevelDTO;
import com.saas.admin.dto.AccessLevelDetailDTO;
import com.saas.admin.dto.AccessLevelRequest;
import com.saas.admin.dto.PermissionGroupDTO;
import com.saas.admin.dto.UpdateStatusRequest;
import com.saas.admin.security.AdminAuthService;
import com.saas.admin.negocio.impl.AccessLevelNegocio;
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
import java.util.Optional;

/**
 * CRUD de níveis de acesso administrativo. Migrado 1:1 do backend-quarkus
 * (com.saas.resource.AdminAccessLevelsResource).
 */
@Path("/api/v1/admin/access-levels")
@Tag(name = "Access Levels", description = "CRUD de níveis de acesso administrativo (perfis de permissões granulares " +
    "atribuídos a usuários administrativos) e consulta da árvore fixa de permissões disponíveis. Todas as " +
    "rotas deste controller pertencem exclusivamente ao contexto administrativo e não devem ser utilizadas " +
    "pelo ambiente cliente.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminAccessLevelsResource {

    @Inject AdminAuthService adminAuth;
    @Inject AccessLevelNegocio accessLevelNegocio;

    /**
     * Retorna as permissões do próprio usuário autenticado.
     * Não exige nenhuma permissão específica além de ser um ADMIN_USER ativo —
     * evita o ciclo onde carregar permissões no login exige uma permissão que
     * ainda não foi carregada.
     */
    @GET
    @Path("/my-permissions")
    @Operation(
        summary = "Retorna as permissões granulares do próprio usuário administrativo autenticado",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Não exige nenhuma permissão granular específica — apenas que o chamador seja " +
            "SUPER_ADMIN ou ADMIN_USER ativo (adminAuth.requireAdminPermission(null)) — para evitar o ciclo em " +
            "que carregar as permissões no login exigiria uma permissão ainda não carregada. Retorna a lista " +
            "de permissionKeys do nível de acesso vinculado ao usuário; se o usuário não tiver nível de acesso " +
            "atribuído, retorna lista vazia."
    )
    @APIResponse(responseCode = "200", description = "Lista de chaves de permissão do usuário autenticado (pode ser vazia).")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo.")
    public Response getMyPermissions() {
        adminAuth.requireAdminPermission(null);

        List<String> perms = accessLevelNegocio.getMyPermissionKeys(adminAuth.currentUserId());
        return Response.ok(Map.of("permissionKeys", perms)).build();
    }

    @GET
    @Path("/permission-tree")
    @Operation(
        summary = "Retorna a árvore fixa de grupos e permissões administrativas disponíveis",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Retorna a estrutura estática (definida em código, não em banco de dados) de grupos " +
            "(dashboard, clients, companies, plans, subscriptions, modules, users, access_levels, trials, " +
            "settings, feature_flags) e suas respectivas permissionKeys, usada para montar a UI de seleção de " +
            "permissões ao criar/editar um nível de acesso. Requer a permissão granular " +
            "'admin.access_levels.view'."
    )
    @APIResponse(responseCode = "200", description = "Árvore de grupos e permissões administrativas disponíveis.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.access_levels.view'.")
    public Response getPermissionTree() {
        adminAuth.requireAdminPermission("admin.access_levels.view");
        List<PermissionGroupDTO> groups = accessLevelNegocio.getPermissionTree();
        return Response.ok(Map.of("groups", groups)).build();
    }

    @GET
    @Operation(
        summary = "Lista os níveis de acesso administrativo cadastrados",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Lista os registros de admin_access_levels, com a contagem de permissões e de usuários " +
            "ADMIN_USER vinculados a cada nível, opcionalmente filtrados por status. Requer a permissão " +
            "granular 'admin.access_levels.view'."
    )
    @APIResponse(responseCode = "200", description = "Lista de níveis de acesso administrativo.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.access_levels.view'.")
    public Response listAccessLevels(
            @Parameter(description = "Filtra pelo status do nível de acesso (ex.: ACTIVE, INACTIVE). Opcional.")
            @QueryParam("status") String status) {
        adminAuth.requireAdminPermission("admin.access_levels.view");

        List<AccessLevelDTO> levels = accessLevelNegocio.list(status);
        return Response.ok(levels).build();
    }

    @GET
    @Path("/{id}")
    @Operation(
        summary = "Retorna o detalhe de um nível de acesso administrativo, com suas permissões",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Retorna os dados do nível de acesso identificado por 'id' e a lista de permissionKeys " +
            "atribuídas a ele. Requer a permissão granular 'admin.access_levels.view'."
    )
    @APIResponse(responseCode = "200", description = "Detalhe do nível de acesso, incluindo suas chaves de permissão.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.access_levels.view'.")
    @APIResponse(responseCode = "404", description = "Nenhum nível de acesso encontrado para o 'id' informado.")
    public Response getAccessLevel(
            @Parameter(description = "Identificador (UUID) do nível de acesso administrativo.", required = true)
            @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.access_levels.view");

        Optional<AccessLevelDetailDTO> level = accessLevelNegocio.get(id);
        if (level.isEmpty()) return Response.status(404).entity(Map.of("error", "Nível não encontrado")).build();
        return Response.ok(level.get()).build();
    }

    @POST
    @Operation(
        summary = "Cria um novo nível de acesso administrativo",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Cria um registro em admin_access_levels a partir de 'name' (obrigatório) e " +
            "'description' (opcional), sempre com status inicial ACTIVE. A lista 'permissionKeys' informada " +
            "no corpo é filtrada contra a árvore fixa de permissões válidas (chaves desconhecidas são " +
            "descartadas silenciosamente) e persistida em admin_access_level_permissions. Requer a permissão " +
            "granular 'admin.access_levels.create'."
    )
    @APIResponse(responseCode = "201", description = "Nível de acesso criado com sucesso, incluindo as chaves de permissão efetivamente salvas.")
    @APIResponse(responseCode = "400", description = "'name' ausente ou em branco.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.access_levels.create'.")
    public Response createAccessLevel(AccessLevelRequest req) {
        adminAuth.requireAdminPermission("admin.access_levels.create");

        String name = req.name();
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();

        AccessLevelDetailDTO created = accessLevelNegocio.create(
            name.trim(), req.description(), req.permissionKeys(), adminAuth.currentUserId());

        return Response.status(201).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(
        summary = "Edita nome, descrição e permissões de um nível de acesso administrativo",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Atualiza 'name' (obrigatório) e 'description' do nível de acesso identificado por 'id', " +
            "e substitui integralmente o conjunto de permissões: todas as permissões atuais em " +
            "admin_access_level_permissions são removidas e recriadas a partir de 'permissionKeys' " +
            "(filtradas contra a árvore fixa de permissões válidas). Requer a permissão granular " +
            "'admin.access_levels.edit'."
    )
    @APIResponse(responseCode = "200", description = "Nível de acesso atualizado com sucesso, incluindo as chaves de permissão efetivamente salvas.")
    @APIResponse(responseCode = "400", description = "'name' ausente ou em branco.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.access_levels.edit'.")
    @APIResponse(responseCode = "404", description = "Nenhum nível de acesso encontrado para o 'id' informado.")
    public Response updateAccessLevel(
            @Parameter(description = "Identificador (UUID) do nível de acesso administrativo.", required = true)
            @PathParam("id") String id,
            AccessLevelRequest req) {
        adminAuth.requireAdminPermission("admin.access_levels.edit");

        String name = req.name();
        if (name == null || name.isBlank())
            return Response.status(400).entity(Map.of("error", "name é obrigatório")).build();

        Optional<List<String>> permKeys = accessLevelNegocio.update(
            id, name.trim(), req.description(), req.permissionKeys(), adminAuth.currentUserId());

        if (permKeys.isEmpty()) return Response.status(404).entity(Map.of("error", "Nível não encontrado")).build();
        return Response.ok(Map.of("ok", true, "permissionKeys", permKeys.get())).build();
    }

    @PATCH
    @Path("/{id}/status")
    @Operation(
        summary = "Ativa ou inativa um nível de acesso administrativo",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Atualiza o status do nível de acesso identificado por 'id' para ACTIVE ou INACTIVE. Ao " +
            "tentar inativar (INACTIVE), a operação é bloqueada se ainda houver usuários ADMIN_USER ativos " +
            "vinculados a esse nível — eles precisam ser reatribuídos antes. Requer a permissão granular " +
            "'admin.access_levels.activate'."
    )
    @APIResponse(responseCode = "200", description = "Status do nível de acesso atualizado com sucesso.")
    @APIResponse(responseCode = "400", description = "'status' ausente ou diferente de ACTIVE/INACTIVE.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.access_levels.activate'.")
    @APIResponse(responseCode = "404", description = "Nenhum nível de acesso encontrado para o 'id' informado.")
    @APIResponse(responseCode = "409", description = "Existem usuários administrativos ativos vinculados a este nível — é necessário reatribuí-los antes de inativar.")
    public Response updateStatus(
            @Parameter(description = "Identificador (UUID) do nível de acesso administrativo.", required = true)
            @PathParam("id") String id,
            UpdateStatusRequest req) {
        adminAuth.requireAdminPermission("admin.access_levels.activate");

        String newStatus = req != null ? req.status() : null;
        if (!List.of("ACTIVE", "INACTIVE").contains(newStatus))
            return Response.status(400).entity(Map.of("error", "Status inválido. Use ACTIVE ou INACTIVE")).build();

        if ("INACTIVE".equals(newStatus)) {
            long userCount = accessLevelNegocio.countActiveAdminUsers(id);
            if (userCount > 0)
                return Response.status(409).entity(Map.of(
                    "error", "Este nível possui " + userCount + " usuário(s) ativo(s). Reatribua-os antes de inativar.",
                    "user_count", userCount
                )).build();
        }

        boolean updated = accessLevelNegocio.updateStatus(id, newStatus, adminAuth.currentUserId());
        if (!updated) return Response.status(404).entity(Map.of("error", "Nível não encontrado")).build();

        return Response.ok(Map.of("ok", true, "status", newStatus)).build();
    }
}
