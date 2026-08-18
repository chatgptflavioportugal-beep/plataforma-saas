package com.saas.admin.controller;

import com.saas.admin.dto.CancelRequest;
import com.saas.admin.dto.TrialCampaignDTO;
import com.saas.admin.dto.TrialCampaignDetailDTO;
import com.saas.admin.dto.TrialCampaignHistoryEntryDTO;
import com.saas.admin.dto.TrialCampaignPageDTO;
import com.saas.admin.dto.TrialCampaignParticipantDTO;
import com.saas.admin.dto.TrialCampaignRequest;
import com.saas.admin.security.AdminAuthService;
import com.saas.admin.service.AdminTrialCampaignService;
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
 * Administração de Trial Campaigns — CRUD, cancelamento, auditoria e relatórios
 * (indicadores + participantes + histórico). Movido de subscription-service
 * para admin-service (único dono de trial_campaigns). Regras de elegibilidade/
 * seleção em tempo real por tenant (checkEligibility/claimSlotOrThrow/
 * resolveCatalogOffer/resolveModuleTrialStatus) continuam em
 * subscription-service.TrialCampaignService — este recurso só cuida do ciclo
 * de vida administrativo das campanhas.
 */
@Path("/api/v1/admin/trial-campaigns")
@Tag(name = "Administration", description = "Administração do ciclo de vida das campanhas de Free Trial da plataforma (trial_campaigns): CRUD, cancelamento, indicadores e histórico de auditoria. Contexto exclusivamente administrativo — não deve ser utilizado pelo ambiente cliente. A elegibilidade e a seleção de campanha em tempo real por tenant, no momento em que o cliente ativa um trial, permanecem em subscription-service.")
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminTrialCampaignResource {

    @Inject AdminAuthService adminAuth;
    @Inject AdminTrialCampaignService trialCampaignService;

    // ─── Listagens ────────────────────────────────────────────────────────────

    @GET
    @Path("/by-plan/{planId}")
    @Operation(
        summary = "Lista as campanhas de Trial de um plano",
        description = "Operação exclusivamente administrativa e de consulta — não deve ser " +
            "utilizada pelo ambiente cliente. Lista todas as campanhas de Trial (qualquer " +
            "status) vinculadas a qualquer módulo do plano informado, ordenadas por nome do " +
            "módulo, prioridade (decrescente) e data de criação (mais recente primeiro)."
    )
    @APIResponse(responseCode = "200", description = "Lista de campanhas de Trial do plano (pode ser vazia).")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.trials.view`.")
    public Response listByPlan(
            @Parameter(description = "ID (UUID) do plano cujas campanhas de Trial serão listadas.", required = true) @PathParam("planId") String planId) {
        adminAuth.requireAdminPermission("admin.trials.view");

        List<TrialCampaignDTO> campaigns = trialCampaignService.listByPlan(planId);
        return Response.ok(campaigns).build();
    }

    @GET
    @Operation(
        summary = "Lista e filtra as campanhas de Trial de toda a plataforma, paginado",
        description = "Operação exclusivamente administrativa e de consulta — não deve ser " +
            "utilizada pelo ambiente cliente. Lista campanhas de Trial de qualquer módulo/" +
            "plano, com filtros combináveis por status, módulo, plano, criador, texto livre " +
            "(`search`, contra o nome), intervalo de `start_date` e disponibilidade de vagas " +
            "(`hasSlots=true` retorna apenas campanhas com `used_slots < max_slots`). Paginação " +
            "via `page`/`size` (`size` limitado a 1-100, padrão 20) e ordenação configurável " +
            "via `sortBy`/`sortDir` (padrão: mais recentes primeiro)."
    )
    @APIResponse(responseCode = "200", description = "Página de campanhas de Trial, com `items`, `total`, `page` e `size`.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.trials.view`.")
    public Response listAll(
        @Parameter(description = "Filtra pelo status exato da campanha (ACTIVE, SCHEDULED, CLOSED ou CANCELLED).") @QueryParam("status") String status,
        @Parameter(description = "Filtra pelo ID (UUID) do módulo da campanha.") @QueryParam("moduleId") String moduleId,
        @Parameter(description = "Filtra pelo ID (UUID) do plano ao qual a campanha pertence.") @QueryParam("planId") String planId,
        @Parameter(description = "Filtra pelo ID (UUID) do administrador que criou a campanha.") @QueryParam("createdBy") String createdBy,
        @Parameter(description = "Busca parcial (ILIKE) pelo nome da campanha.") @QueryParam("search") String search,
        @Parameter(description = "Data inicial (inclusive) do intervalo de `start_date` da campanha (ISO-8601).") @QueryParam("startDateFrom") String startDateFrom,
        @Parameter(description = "Data final (inclusive) do intervalo de `start_date` da campanha (ISO-8601).") @QueryParam("startDateTo") String startDateTo,
        @Parameter(description = "Quando `true`, retorna apenas campanhas com vagas disponíveis (`used_slots < max_slots`).") @QueryParam("hasSlots") Boolean hasSlots,
        @Parameter(description = "Campo de ordenação: name, priority, status, startDate, usedSlots, planName ou moduleName (padrão: data de criação).") @QueryParam("sortBy") String sortBy,
        @Parameter(description = "Direção da ordenação: asc ou desc (padrão: desc quando `sortBy` não é informado, asc caso contrário).") @QueryParam("sortDir") String sortDir,
        @Parameter(description = "Número da página, começando em 1 (padrão 1).") @QueryParam("page") Integer page,
        @Parameter(description = "Quantidade de itens por página, entre 1 e 100 (padrão 20).") @QueryParam("size") Integer size
    ) {
        adminAuth.requireAdminPermission("admin.trials.view");

        TrialCampaignPageDTO result = trialCampaignService.listAll(
            status, moduleId, planId, createdBy, search, startDateFrom, startDateTo, hasSlots,
            sortBy, sortDir, page, size);

        return Response.ok(result).build();
    }

    @GET
    @Path("/{id}")
    @Operation(
        summary = "Detalha uma campanha de Trial, com indicadores de participação",
        description = "Operação exclusivamente administrativa e de consulta — não deve ser " +
            "utilizada pelo ambiente cliente. Retorna os dados completos da campanha (incluindo " +
            "dados do módulo e do plano) somados a indicadores calculados sobre " +
            "module_trial_history/profile_module_subscriptions: total de participantes, taxa " +
            "de conversão (`conversionPercent`, arredondada a 1 casa decimal) e a contagem de " +
            "participantes ativos, expirados e cancelados."
    )
    @APIResponse(responseCode = "200", description = "Detalhe da campanha, com os indicadores de participação agregados.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.trials.view`.")
    @APIResponse(responseCode = "404", description = "Nenhuma campanha de Trial encontrada para o `id` informado.")
    public Response getDetail(
            @Parameter(description = "ID (UUID) da campanha de Trial.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.trials.view");

        TrialCampaignDetailDTO detail = trialCampaignService.getDetail(id)
            .orElseThrow(() -> new NotFoundException("Campanha de Trial não encontrada"));
        return Response.ok(detail).build();
    }

    @GET
    @Path("/{id}/participants")
    @Operation(
        summary = "Lista os participantes (tenants) de uma campanha de Trial",
        description = "Operação exclusivamente administrativa e de consulta — não deve ser " +
            "utilizada pelo ambiente cliente. Lista, a partir de module_trial_history, cada " +
            "tenant que participou da campanha (nome, tipo INDIVIDUAL/COMPANY, usuário que " +
            "iniciou o trial), datas de início/fim/cancelamento, o status atual da assinatura " +
            "vinculada e se o participante se converteu em cliente pagante " +
            "(`becameCustomer`). Não valida se a campanha existe — para um `id` inexistente, " +
            "retorna lista vazia."
    )
    @APIResponse(responseCode = "200", description = "Lista de participantes da campanha (pode ser vazia).")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.trials.view`.")
    public Response listParticipants(
            @Parameter(description = "ID (UUID) da campanha de Trial.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.trials.view");

        List<TrialCampaignParticipantDTO> participants = trialCampaignService.listParticipants(id);
        return Response.ok(participants).build();
    }

    @GET
    @Path("/{id}/history")
    @Operation(
        summary = "Lista o histórico de auditoria administrativa de uma campanha de Trial",
        description = "Operação exclusivamente administrativa e de consulta — não deve ser " +
            "utilizada pelo ambiente cliente. Lista, em ordem cronológica, os eventos de " +
            "auditoria (admin_audit_logs) registrados para a campanha — criação, edição, " +
            "encerramento, cancelamento e substituição automática por nova versão do plano — " +
            "com a ação, o administrador responsável e a data. Não valida se a campanha " +
            "existe — para um `id` inexistente, retorna lista vazia."
    )
    @APIResponse(responseCode = "200", description = "Lista de eventos de auditoria da campanha, em ordem cronológica (pode ser vazia).")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.trials.view`.")
    public Response history(
            @Parameter(description = "ID (UUID) da campanha de Trial.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.trials.view");

        List<TrialCampaignHistoryEntryDTO> history = trialCampaignService.history(id);
        return Response.ok(history).build();
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    @POST
    @Operation(
        summary = "Cria uma nova campanha de Trial para um módulo de um plano",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Cria a campanha vinculada a um `planVersionModuleId` " +
            "existente, com `name` obrigatório, `days` (1-365) e `maxSlots` (>= 1) " +
            "obrigatórios, `status` inicial restrito a ACTIVE/SCHEDULED/CLOSED (o status " +
            "CANCELLED só é alcançável via `POST /{id}/cancel`), e `startDate`/`endDate` " +
            "opcionais (quando ambos informados, `endDate` não pode ser anterior a " +
            "`startDate`). Campanhas de Trial não são permitidas para módulos do plano Free " +
            "(que já é gratuito por natureza) — a tentativa é registrada em auditoria como " +
            "`trial_campaign.creation_denied` antes de ser recusada."
    )
    @APIResponse(responseCode = "200", description = "Campanha criada com sucesso; retorna o `id` gerado.")
    @APIResponse(responseCode = "400", description = "`planVersionModuleId` ou `name` ausentes, `days`/`maxSlots` fora do intervalo permitido, `status` inválido, `endDate` anterior a `startDate`, data em formato inválido, ou o módulo do plano informado é do plano Free (não permite Trial).")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.trials.create`.")
    @APIResponse(responseCode = "404", description = "Nenhum módulo de plano (`plan_version_modules`) encontrado para o `planVersionModuleId` informado.")
    public Response create(TrialCampaignRequest req) {
        adminAuth.requireAdminPermission("admin.trials.create");
        String userId = adminAuth.currentUserId();

        var id = trialCampaignService.create(req, userId);
        return Response.ok(Map.of("id", id.toString(), "created", true)).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(
        summary = "Atualiza uma campanha de Trial existente",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Substitui os dados da campanha. Regras específicas: (1) se a " +
            "campanha já está CANCELLED, o `status` do corpo precisa continuar CANCELLED — o " +
            "cancelamento não pode ser revertido por aqui, apenas via edição de outros campos; " +
            "(2) se a campanha já possui participantes (module_trial_history), `days` e " +
            "`maxSlots` ficam congelados (não são alterados, mesmo se enviados) para não " +
            "afetar retroativamente quem já participou — a resposta indica isso em " +
            "`termsLocked=true`; para mudar os termos, é preciso criar uma nova campanha."
    )
    @APIResponse(responseCode = "200", description = "Campanha atualizada com sucesso; retorna `termsLocked` indicando se `days`/`maxSlots` foram preservados por já haver participantes.")
    @APIResponse(responseCode = "400", description = "`status` inválido para o estado atual da campanha (ex.: tentar sair de CANCELLED, ou usar um status fora de ACTIVE/SCHEDULED/CLOSED), `endDate` anterior a `startDate`, data inválida, ou (sem participantes) `days`/`maxSlots` fora do intervalo permitido.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.trials.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhuma campanha de Trial encontrada para o `id` informado.")
    public Response update(
            @Parameter(description = "ID (UUID) da campanha de Trial a atualizar.", required = true) @PathParam("id") String id,
            TrialCampaignRequest req) {
        adminAuth.requireAdminPermission("admin.trials.edit");
        String userId = adminAuth.currentUserId();

        Optional<AdminTrialCampaignService.UpdateResult> result = trialCampaignService.update(id, req, userId);
        AdminTrialCampaignService.UpdateResult r = result
            .orElseThrow(() -> new NotFoundException("Campanha de Trial não encontrada"));

        return Response.ok(Map.of("id", id, "updated", true, "termsLocked", r.termsLocked())).build();
    }

    @POST
    @Path("/{id}/close")
    @Operation(
        summary = "Encerra uma campanha de Trial, definindo o status como CLOSED",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Não recebe corpo: define diretamente `status = 'CLOSED'` " +
            "independentemente do status atual da campanha. Diferente de `POST /{id}/cancel`, " +
            "não grava motivo de cancelamento nem restringe o status de origem — apenas " +
            "encerra a campanha para novas adesões."
    )
    @APIResponse(responseCode = "200", description = "Campanha encerrada com sucesso; retorna `status = CLOSED`.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.trials.edit`.")
    @APIResponse(responseCode = "404", description = "Nenhuma campanha de Trial encontrada para o `id` informado.")
    public Response close(
            @Parameter(description = "ID (UUID) da campanha de Trial a encerrar.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.trials.edit");
        String userId = adminAuth.currentUserId();

        boolean closed = trialCampaignService.close(id, userId);
        if (!closed) throw new NotFoundException("Campanha de Trial não encontrada");

        return Response.ok(Map.of("id", id, "status", "CLOSED")).build();
    }

    @POST
    @Path("/{id}/cancel")
    @Operation(
        summary = "Cancela uma campanha de Trial, com motivo de auditoria",
        description = "Operação exclusivamente administrativa — não deve ser utilizada pelo " +
            "ambiente cliente. Só cancela campanhas em status ACTIVE ou SCHEDULED, marcando " +
            "`status = CANCELLED`, `cancelled_at` e o `reason` informado (ou um motivo padrão " +
            "quando omitido). Cancelar apenas impede novas ativações do Trial pela campanha — " +
            "participantes que já iniciaram continuam normalmente até o fim do período " +
            "contratado; nenhuma outra tabela é alterada."
    )
    @APIResponse(responseCode = "200", description = "Campanha cancelada com sucesso; retorna `status = CANCELLED`.")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Usuário não é administrador da plataforma, está inativo, ou não possui a permissão `admin.trials.cancel`.")
    @APIResponse(responseCode = "404", description = "Nenhuma campanha de Trial encontrada para o `id` informado com status ACTIVE ou SCHEDULED (inexistente ou já não pode mais ser cancelada).")
    public Response cancel(
            @Parameter(description = "ID (UUID) da campanha de Trial a cancelar.", required = true) @PathParam("id") String id,
            CancelRequest req) {
        adminAuth.requireAdminPermission("admin.trials.cancel");
        String userId = adminAuth.currentUserId();

        boolean cancelled = trialCampaignService.cancel(id, req, userId);
        if (!cancelled)
            return Response.status(404).entity(Map.of("error", "Campanha não encontrada ou não pode mais ser cancelada")).build();

        return Response.ok(Map.of("id", id, "status", "CANCELLED")).build();
    }
}
