package com.saas.admin.resource;

import com.saas.admin.dto.CustomerDetailDTO;
import com.saas.admin.dto.CustomerSummaryDTO;
import com.saas.admin.dto.DashboardStatsDTO;
import com.saas.admin.dto.SystemAdminDTO;
import com.saas.admin.dto.TenantDetailDTO;
import com.saas.admin.dto.TenantSummaryDTO;
import com.saas.admin.dto.UpdateStatusRequest;
import com.saas.admin.security.AdminAuthService;
import com.saas.admin.negocio.impl.AdminGeneralNegocio;
import com.saas.admin.negocio.impl.TenantNegocio;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tenants, clientes, administradores do sistema, assinaturas (visão admin) e stats
 * do dashboard admin. Migrado 1:1 do backend-quarkus (com.saas.resource.AdminResource).
 */
@Path("/api/v1/admin")
@Tag(name = "Administration", description = "Operações administrativas gerais da plataforma: dashboard, tenants (empresas), " +
    "clientes finais, administradores do sistema e visão consolidada de assinaturas. " +
    "Todas as rotas deste controller pertencem exclusivamente ao contexto administrativo e não devem ser " +
    "utilizadas pelo ambiente cliente.")
@Authenticated
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT emitido pelo Supabase Auth (login do usuário administrativo). Enviar como 'Authorization: Bearer <token>'."
)
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    private static final List<String> VALID_TENANT_STATUSES = List.of("trial", "active", "suspended", "cancelled");

    @Inject TenantNegocio tenantNegocio;
    @Inject AdminGeneralNegocio adminGeneralNegocio;
    @Inject AdminAuthService adminAuth;

    // ----------------------------------------------------------------
    // Dashboard stats
    // ----------------------------------------------------------------

    @GET
    @Path("/stats")
    @Operation(
        summary = "Retorna estatísticas consolidadas do dashboard administrativo",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Executa uma série de contagens agregadas sobre tenants, usuários, jobs de PDF e vínculos " +
            "de membros para alimentar o dashboard inicial da área Admin: total de tenants do tipo 'business' " +
            "e sua distribuição por status (ativos, em trial, suspensos), total de usuários (excluindo perfis " +
            "administrativos), total de jobs de PDF, quantos usuários possuem perfil individual, total de " +
            "vínculos de membros em empresas, usuários que participam de mais de uma empresa, empresas sem " +
            "membros extras e empresas com membros convidados aceitos. Requer a permissão granular " +
            "'admin.dashboard.view'."
    )
    @APIResponse(responseCode = "200", description = "Objeto com os contadores consolidados do dashboard.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.dashboard.view'.")
    public Response stats() {
        adminAuth.requireAdminPermission("admin.dashboard.view");
        DashboardStatsDTO result = adminGeneralNegocio.stats();
        return Response.ok(result).build();
    }

    // ----------------------------------------------------------------
    // Tenants
    // ----------------------------------------------------------------

    @GET
    @Path("/tenants")
    @Operation(
        summary = "Lista empresas (tenants) da plataforma para a visão administrativa",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Lista os tenants do tipo 'business' cadastrados na plataforma, com filtros opcionais de " +
            "busca textual, status do tenant e presença de membros além do owner. Delegado a " +
            "TenantNegocio.listAdminTenants. Requer a permissão granular 'admin.companies.view'."
    )
    @APIResponse(responseCode = "200", description = "Lista de empresas (tenants) que atendem aos filtros informados.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.companies.view'.")
    public Response listTenants(
            @Parameter(description = "Filtro de busca textual por nome/slug da empresa (opcional).")
            @QueryParam("search") String search,
            @Parameter(description = "Filtra pelo status do tenant (ex.: trial, active, suspended, cancelled). Opcional.")
            @QueryParam("status") String status,
            @Parameter(description = "Quando informado, filtra empresas que possuem (true) ou não possuem (false) membros além do owner.")
            @QueryParam("has_extra_members") Boolean hasExtraMembers) {
        adminAuth.requireAdminPermission("admin.companies.view");

        List<TenantSummaryDTO> tenants = tenantNegocio.listAdminTenants(search, status, hasExtraMembers);
        return Response.ok(tenants).build();
    }

    @GET
    @Path("/tenants/{id}")
    @Operation(
        summary = "Retorna o detalhe completo de uma empresa (tenant) para a visão administrativa",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Retorna os dados detalhados do tenant identificado por 'id', conforme montado por " +
            "TenantNegocio.getAdminTenantDetail. Requer a permissão granular 'admin.companies.detail'."
    )
    @APIResponse(responseCode = "200", description = "Detalhe completo da empresa (tenant).")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.companies.detail'.")
    @APIResponse(responseCode = "404", description = "Nenhuma empresa encontrada para o 'id' informado.")
    public Response getTenantDetail(
            @Parameter(description = "Identificador (UUID) do tenant/empresa.", required = true)
            @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.companies.detail");

        Optional<TenantDetailDTO> detail = tenantNegocio.getAdminTenantDetail(id);
        if (detail.isEmpty())
            return Response.status(404).entity(Map.of("error", "Empresa não encontrada")).build();
        return Response.ok(detail.get()).build();
    }

    // ----------------------------------------------------------------
    // Clientes — listagem com estrutura completa de perfis
    // ----------------------------------------------------------------

    @GET
    @Path("/customers")
    @Operation(
        summary = "Lista clientes finais da plataforma com estrutura completa de perfis",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Lista os usuários com perfil de cliente (exclui SUPER_ADMIN/ADMIN/SUPPORT/FINANCE_ADMIN), " +
            "informando se possuem perfil individual, quantas empresas próprias (owner) e quantas empresas " +
            "como membro (não-owner) cada cliente possui. Suporta filtros combináveis de busca textual " +
            "(nome/e-mail), presença de perfil individual, posse de empresa própria, participação como membro, " +
            "status ativo/inativo e tipo de perfil predominante. Requer a permissão granular 'admin.clients.view'."
    )
    @APIResponse(responseCode = "200", description = "Lista de clientes que atendem aos filtros informados, com contagem de perfis.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.clients.view'.")
    public Response listCustomers(
            @Parameter(description = "Filtro de busca textual por nome completo ou e-mail do cliente (opcional).")
            @QueryParam("search") String search,
            @Parameter(description = "Quando informado, filtra clientes que possuem (true) ou não possuem (false) perfil individual.")
            @QueryParam("has_individual") Boolean hasIndividual,
            @Parameter(description = "Quando informado, filtra clientes que possuem (true) ou não possuem (false) ao menos uma empresa própria (owner).")
            @QueryParam("has_owned_company") Boolean hasOwnedCompany,
            @Parameter(description = "Quando informado, filtra clientes que são (true) ou não são (false) membro (não-owner) de alguma empresa.")
            @QueryParam("is_member") Boolean isMember,
            @Parameter(description = "Quando informado, filtra clientes ativos (true) ou inativos (false).")
            @QueryParam("is_active") Boolean isActive,
            @Parameter(description = "Filtra pelo tipo de perfil predominante: 'individual', 'owned_company' ou 'member_company' (opcional).")
            @QueryParam("profile_type") String profileType) {
        adminAuth.requireAdminPermission("admin.clients.view");

        List<CustomerSummaryDTO> customers = adminGeneralNegocio.listCustomers(
            search, hasIndividual, hasOwnedCompany, isMember, isActive, profileType);
        return Response.ok(customers).build();
    }

    // ----------------------------------------------------------------
    // Clientes — detalhe por ID
    // ----------------------------------------------------------------

    @GET
    @Path("/customers/{id}")
    @Operation(
        summary = "Retorna o detalhe completo de um cliente final",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Retorna os dados do cliente (excluindo perfis administrativos), seu perfil individual " +
            "(quando existir), a lista de empresas que possui como owner (com plano e quantidade de membros) e " +
            "a lista de empresas das quais participa como membro (com o papel e quem o convidou, quando " +
            "aplicável). Requer a permissão granular 'admin.clients.detail'."
    )
    @APIResponse(responseCode = "200", description = "Detalhe completo do cliente, incluindo perfil individual e empresas relacionadas.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.clients.detail'.")
    @APIResponse(responseCode = "404", description = "Nenhum cliente (não administrativo) encontrado para o 'id' informado.")
    public Response getCustomerDetail(
            @Parameter(description = "Identificador (UUID) do usuário cliente.", required = true)
            @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.clients.detail");

        Optional<CustomerDetailDTO> detail = adminGeneralNegocio.getCustomerDetail(id);
        if (detail.isEmpty())
            return Response.status(404).entity(Map.of("error", "Usuário não encontrado")).build();
        return Response.ok(detail.get()).build();
    }

    // ----------------------------------------------------------------
    // Compatibilidade — redireciona chamadas legadas de company-users
    // ----------------------------------------------------------------

    @GET
    @Path("/company-users")
    @Operation(
        summary = "[Legado] Alias de compatibilidade para a listagem de clientes",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Mantido apenas por compatibilidade com chamadas legadas que ainda usam o caminho " +
            "'/company-users'; delega integralmente para listCustomers (GET /customers) sem nenhum filtro " +
            "aplicado, e portanto exige a mesma permissão granular 'admin.clients.view'. Novas integrações " +
            "devem usar diretamente GET /api/v1/admin/customers.",
        deprecated = true
    )
    @APIResponse(responseCode = "200", description = "Lista completa de clientes (equivalente a GET /customers sem filtros).")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.clients.view'.")
    public Response listCompanyUsersLegacy() {
        return listCustomers(null, null, null, null, null, null);
    }

    // ----------------------------------------------------------------
    // Administradores do Sistema (somente roles administrativos)
    // ----------------------------------------------------------------

    @GET
    @Path("/system-admins")
    @Operation(
        summary = "Lista administradores do sistema com papéis legados (SUPER_ADMIN/ADMIN/SUPPORT/FINANCE_ADMIN)",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Lista os usuários cujo system_role é SUPER_ADMIN, ADMIN, SUPPORT ou FINANCE_ADMIN — " +
            "papéis administrativos legados, distintos do modelo mais recente de ADMIN_USER com nível de " +
            "acesso configurável (gerido por AdminUsersResource). Não recebe filtros. Requer a permissão " +
            "granular 'admin.users.view'."
    )
    @APIResponse(responseCode = "200", description = "Lista dos administradores do sistema com papéis legados, ordenada por papel e data de criação.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.users.view'.")
    public Response listSystemAdmins() {
        adminAuth.requireAdminPermission("admin.users.view");

        List<SystemAdminDTO> admins = adminGeneralNegocio.listSystemAdmins();
        return Response.ok(admins).build();
    }

    // Listagem/resumo de assinaturas (GET /subscriptions, /subscriptions/summary)
    // e cancelamento/reativação (POST /subscriptions/{id}/cancel|reactivate) vivem
    // juntos em AdminSubscriptionResource — duas classes JAX-RS mapeando o mesmo
    // path literal "/api/v1/admin/subscriptions" faz o RESTEasy Reactive rotear
    // tudo para uma só delas e 404 nos métodos da outra.

    // ----------------------------------------------------------------
    // Gestão Global — Bloqueio/Desbloqueio/Suspensão/Ativação
    // ----------------------------------------------------------------

    @PATCH
    @Path("/tenants/{id}/status")
    @Operation(
        summary = "Atualiza o status de uma empresa (tenant)",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Altera o status do tenant informado para um dos valores válidos: trial, active, " +
            "suspended, cancelled. A permissão exigida varia conforme o status de destino: " +
            "'admin.companies.activate' quando o novo status é 'active', ou 'admin.companies.deactivate' " +
            "para os demais valores. A alteração é registrada em log de auditoria (AdminAuditNegocio)."
    )
    @APIResponse(responseCode = "200", description = "Status da empresa atualizado com sucesso; retorna o id e o novo status.")
    @APIResponse(responseCode = "400", description = "Corpo ausente ou 'status' inválido (deve ser um de: trial, active, suspended, cancelled).")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.companies.activate'/'admin.companies.deactivate' correspondente ao status solicitado.")
    @APIResponse(responseCode = "404", description = "Nenhuma empresa encontrada para o 'id' informado.")
    public Response updateTenantStatus(
            @Parameter(description = "Identificador (UUID) do tenant/empresa.", required = true)
            @PathParam("id") String id,
            UpdateStatusRequest req) {
        String status = req != null ? req.status() : null;
        if (status == null || !VALID_TENANT_STATUSES.contains(status))
            return Response.status(400).entity(Map.of("error", "status inválido: deve ser um de " + VALID_TENANT_STATUSES)).build();

        adminAuth.requireAdminPermission("active".equals(status) ? "admin.companies.activate" : "admin.companies.deactivate");

        boolean updated = tenantNegocio.updateStatus(id, status, adminAuth.currentUserId());
        if (!updated) return Response.status(404).entity(Map.of("error", "Empresa não encontrada")).build();

        return Response.ok(Map.of("id", id, "status", status)).build();
    }

    @PATCH
    @Path("/customers/{id}/status")
    @Operation(
        summary = "Ativa ou inativa um cliente final",
        description = "Endpoint exclusivo do contexto administrativo — não deve ser utilizado pelo ambiente " +
            "cliente. Altera o campo is_active do cliente informado. O único valor de 'status' aceito é " +
            "'active' ou 'inactive'. A permissão exigida varia conforme o status de destino: " +
            "'admin.clients.activate' quando o novo status é 'active', ou 'admin.clients.deactivate' quando é " +
            "'inactive'. A alteração é registrada em log de auditoria (AdminAuditNegocio)."
    )
    @APIResponse(responseCode = "200", description = "Status do cliente atualizado com sucesso; retorna o id e o novo status.")
    @APIResponse(responseCode = "400", description = "'status' ausente ou diferente de 'active'/'inactive'.")
    @APIResponse(responseCode = "401", description = "Requisição sem JWT válido do Supabase Auth.")
    @APIResponse(responseCode = "403", description = "Usuário autenticado não é SUPER_ADMIN/ADMIN_USER ativo, ou não possui a permissão 'admin.clients.activate'/'admin.clients.deactivate' correspondente ao status solicitado.")
    @APIResponse(responseCode = "404", description = "Nenhum cliente encontrado para o 'id' informado.")
    public Response updateCustomerStatus(
            @Parameter(description = "Identificador (UUID) do usuário cliente.", required = true)
            @PathParam("id") String id,
            UpdateStatusRequest req) {
        String status = req != null ? req.status() : null;
        if (!List.of("active", "inactive").contains(status))
            return Response.status(400).entity(Map.of("error", "status inválido: deve ser 'active' ou 'inactive'")).build();

        adminAuth.requireAdminPermission("active".equals(status) ? "admin.clients.activate" : "admin.clients.deactivate");

        boolean isActive = "active".equals(status);
        boolean updated = adminGeneralNegocio.updateCustomerStatus(id, isActive, status, adminAuth.currentUserId());
        if (!updated) return Response.status(404).entity(Map.of("error", "Cliente não encontrado")).build();

        return Response.ok(Map.of("id", id, "status", status)).build();
    }
}
