package com.saas.admin.controller;

import com.saas.admin.security.AdminAuthService;
import com.saas.admin.service.AdminAuditService;
import com.saas.admin.service.TrialCampaignAdminService;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    // Status que podem ser atribuídos diretamente por criação/edição. CANCELLED só
    // é alcançável pela ação dedicada POST /{id}/cancel (confirmação + auditoria).
    private static final List<String> VALID_REQUEST_STATUSES = List.of("ACTIVE", "SCHEDULED", "CLOSED");

    @Inject EntityManager em;
    @Inject AdminAuthService adminAuth;
    @Inject AdminAuditService auditService;
    @Inject TrialCampaignAdminService trialCampaignAdminService;

    public record TrialCampaignRequest(
        String planVersionModuleId,
        String name,
        String status,
        Integer days,
        Integer maxSlots,
        String startDate,
        String endDate,
        String notes,
        Integer priority
    ) {}

    // Colunas comuns a listByPlan/listAll/getDetail — mantidas em um só lugar para
    // não deixar os três SELECTs divergirem ao adicionar campos (índices 0-19).
    private static final String COMMON_COLUMNS =
        "tc.id::text, tc.plan_version_module_id::text, pm.id::text, pm.name, " +
        "tc.name, tc.status, tc.days, tc.max_slots, tc.used_slots, " +
        "tc.start_date::text, tc.end_date::text, tc.notes, tc.priority, " +
        "tc.created_at::text, tc.updated_at::text, " +
        "tc.created_by_user_id::text, cu.full_name, " +
        "tc.updated_by_user_id::text, uu.full_name, " +
        "(tc.status = 'CLOSED' AND tc.end_date IS NOT NULL AND tc.end_date < CURRENT_DATE) AS expired ";

    private static final String COMMON_JOINS =
        "FROM trial_campaigns tc " +
        "JOIN plan_version_modules pvm ON pvm.id = tc.plan_version_module_id " +
        "JOIN platform_modules pm ON pm.id = pvm.module_id " +
        "LEFT JOIN user_profiles cu ON cu.id = tc.created_by_user_id " +
        "LEFT JOIN user_profiles uu ON uu.id = tc.updated_by_user_id ";

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
    @SuppressWarnings("unchecked")
    public Response listByPlan(
            @Parameter(description = "ID (UUID) do plano cujas campanhas de Trial serão listadas.", required = true) @PathParam("planId") String planId) {
        adminAuth.requireAdminPermission("admin.trials.view");

        List<Object[]> rows = em.createNativeQuery(
            "SELECT " + COMMON_COLUMNS + COMMON_JOINS +
            "WHERE pvm.plan_id::text = :planId " +
            "ORDER BY pm.name, tc.priority DESC, tc.created_at DESC"
        ).setParameter("planId", planId).getResultList();

        return Response.ok(rows.stream().map(AdminTrialCampaignResource::mapRow).collect(Collectors.toList())).build();
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
    @SuppressWarnings("unchecked")
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

        StringBuilder where = new StringBuilder("WHERE 1 = 1 ");
        Map<String, Object> params = new LinkedHashMap<>();
        if (status != null && !status.isBlank()) {
            where.append("AND tc.status = :status ");
            params.put("status", status);
        }
        if (moduleId != null && !moduleId.isBlank()) {
            where.append("AND pm.id::text = :moduleId ");
            params.put("moduleId", moduleId);
        }
        if (planId != null && !planId.isBlank()) {
            where.append("AND pvm.plan_id::text = :planId ");
            params.put("planId", planId);
        }
        if (createdBy != null && !createdBy.isBlank()) {
            where.append("AND tc.created_by_user_id::text = :createdBy ");
            params.put("createdBy", createdBy);
        }
        if (search != null && !search.isBlank()) {
            where.append("AND tc.name ILIKE :search ");
            params.put("search", "%" + search + "%");
        }
        if (startDateFrom != null && !startDateFrom.isBlank()) {
            where.append("AND tc.start_date >= CAST(:startDateFrom AS date) ");
            params.put("startDateFrom", startDateFrom);
        }
        if (startDateTo != null && !startDateTo.isBlank()) {
            where.append("AND tc.start_date <= CAST(:startDateTo AS date) ");
            params.put("startDateTo", startDateTo);
        }
        if (Boolean.TRUE.equals(hasSlots)) {
            where.append("AND tc.used_slots < tc.max_slots ");
        }

        int safeSize = (size == null || size < 1 || size > 100) ? 20 : size;
        int safePage = (page == null || page < 1) ? 1 : page;
        int offset = (safePage - 1) * safeSize;

        String orderColumn = switch (sortBy == null ? "" : sortBy) {
            case "name" -> "tc.name";
            case "priority" -> "tc.priority";
            case "status" -> "tc.status";
            case "startDate" -> "tc.start_date";
            case "usedSlots" -> "tc.used_slots";
            case "planName" -> "p.name";
            case "moduleName" -> "pm.name";
            default -> "tc.created_at";
        };
        String orderDir;
        if (sortDir != null && !sortDir.isBlank()) {
            orderDir = "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
        } else {
            orderDir = sortBy == null ? "DESC" : "ASC"; // sem sortBy: mais recentes primeiro
        }

        var countQuery = em.createNativeQuery(
            "SELECT COUNT(*) " + COMMON_JOINS + "JOIN plans p ON p.id = pvm.plan_id " + where
        );
        params.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        var dataQuery = em.createNativeQuery(
            "SELECT " + COMMON_COLUMNS + ", p.name AS plan_name, p.code AS plan_code, p.version AS plan_version " +
            COMMON_JOINS + "JOIN plans p ON p.id = pvm.plan_id " + where +
            "ORDER BY " + orderColumn + " " + orderDir + ", tc.id " +
            "LIMIT :limit OFFSET :offset"
        );
        params.forEach(dataQuery::setParameter);
        dataQuery.setParameter("limit", safeSize);
        dataQuery.setParameter("offset", offset);
        List<Object[]> rows = dataQuery.getResultList();

        List<Map<String, Object>> items = rows.stream().map(row -> {
            Map<String, Object> m = mapRow(row);
            m.put("planName", row[20]);
            m.put("planCode", row[21]);
            m.put("planVersion", row[22] != null ? ((Number) row[22]).intValue() : null);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
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
    @SuppressWarnings("unchecked")
    public Response getDetail(
            @Parameter(description = "ID (UUID) da campanha de Trial.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.trials.view");

        Object[] row;
        try {
            row = (Object[]) em.createNativeQuery(
                "SELECT " + COMMON_COLUMNS +
                ", pm.slug, pm.icon_path, p.name, p.code, p.version, pvm.monthly_price, pvm.annual_monthly_price " +
                COMMON_JOINS + "JOIN plans p ON p.id = pvm.plan_id " +
                "WHERE tc.id::text = :id"
            ).setParameter("id", id).getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("Campanha de Trial não encontrada");
        }

        Map<String, Object> m = mapRow(row);
        m.put("moduleSlug",       row[20]);
        m.put("moduleIcon",       row[21]);
        m.put("planName",         row[22]);
        m.put("planCode",         row[23]);
        m.put("planVersion",      row[24] != null ? ((Number) row[24]).intValue() : null);
        m.put("planMonthlyPrice", row[25]);
        m.put("planAnnualPrice",  row[26]);

        long totalParticipants = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM module_trial_history WHERE trial_campaign_id::text = :id"
        ).setParameter("id", id).getSingleResult()).longValue();

        long converted = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM module_trial_history WHERE trial_campaign_id::text = :id AND became_customer = TRUE"
        ).setParameter("id", id).getSingleResult()).longValue();

        m.put("totalParticipants", totalParticipants);
        double conversionPercent = totalParticipants == 0 ? 0.0 : (converted * 100.0 / totalParticipants);
        m.put("conversionPercent", Math.round(conversionPercent * 10.0) / 10.0);

        List<Object[]> statusCounts = em.createNativeQuery(
            "SELECT pms.status, COUNT(*) FROM profile_module_subscriptions pms " +
            "JOIN module_trial_history h ON h.id = pms.trial_history_id " +
            "WHERE h.trial_campaign_id::text = :id " +
            "GROUP BY pms.status"
        ).setParameter("id", id).getResultList();

        long active = 0, expiredCount = 0, cancelledCount = 0;
        for (Object[] sc : statusCounts) {
            String st = (String) sc[0];
            long count = ((Number) sc[1]).longValue();
            if ("TRIAL".equals(st)) active += count;
            else if ("EXPIRED".equals(st)) expiredCount += count;
            else if ("TRIAL_CANCELLED".equals(st) || "CANCELED".equals(st)) cancelledCount += count;
        }
        m.put("participantsActive",    active);
        m.put("participantsExpired",   expiredCount);
        m.put("participantsCancelled", cancelledCount);

        return Response.ok(m).build();
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
    @SuppressWarnings("unchecked")
    public Response listParticipants(
            @Parameter(description = "ID (UUID) da campanha de Trial.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.trials.view");

        List<Object[]> rows = em.createNativeQuery(
            "SELECT t.id::text, t.name, t.type, up.full_name, au.email, " +
            "h.trial_started_at::text, h.trial_finished_at::text, h.trial_canceled_at::text, " +
            "pms.status, h.became_customer " +
            "FROM module_trial_history h " +
            "JOIN tenants t ON t.id = h.tenant_id " +
            "LEFT JOIN user_profiles up ON up.id = h.started_by_user_id " +
            "LEFT JOIN auth.users au ON au.id = h.started_by_user_id " +
            "LEFT JOIN profile_module_subscriptions pms ON pms.trial_history_id = h.id " +
            "WHERE h.trial_campaign_id::text = :id " +
            "ORDER BY h.trial_started_at DESC"
        ).setParameter("id", id).getResultList();

        return Response.ok(rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tenantId",       row[0]);
            m.put("tenantName",     row[1]);
            m.put("tenantType",     "individual".equals(row[2]) ? "INDIVIDUAL" : "COMPANY");
            m.put("userName",       row[3]);
            m.put("userEmail",      row[4]);
            m.put("startedAt",      row[5]);
            m.put("finishedAt",     row[6]);
            m.put("canceledAt",     row[7]);
            m.put("status",         row[8]);
            m.put("becameCustomer", Boolean.TRUE.equals(row[9]));
            return m;
        }).collect(Collectors.toList())).build();
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
    @SuppressWarnings("unchecked")
    public Response history(
            @Parameter(description = "ID (UUID) da campanha de Trial.", required = true) @PathParam("id") String id) {
        adminAuth.requireAdminPermission("admin.trials.view");

        List<Object[]> rows = em.createNativeQuery(
            "SELECT al.action, up.full_name, al.created_at::text " +
            "FROM admin_audit_logs al " +
            "LEFT JOIN user_profiles up ON up.id = al.actor_user_id " +
            "WHERE al.resource = 'trial_campaigns' AND al.resource_id = :id " +
            "ORDER BY al.created_at ASC"
        ).setParameter("id", id).getResultList();

        return Response.ok(rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("action",    row[0]);
            m.put("actorName", row[1]);
            m.put("createdAt", row[2]);
            return m;
        }).collect(Collectors.toList())).build();
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    @POST
    @Transactional
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

        if (req.planVersionModuleId() == null || req.planVersionModuleId().isBlank())
            throw new BadRequestException("planVersionModuleId é obrigatório");
        if (req.name() == null || req.name().isBlank())
            throw new BadRequestException("name é obrigatório");
        validateTerms(req.days(), req.maxSlots());
        String status = validateStatus(req.status());
        validateDates(req.startDate(), req.endDate());

        long pvmExists = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM plan_version_modules WHERE id::text = :id"
        ).setParameter("id", req.planVersionModuleId()).getSingleResult()).longValue();
        if (pvmExists == 0) throw new NotFoundException("Módulo do plano não encontrado");

        // O plano Free já é a modalidade gratuita permanente do módulo — Trial não
        // faz sentido nele. Validação de negócio fica na camada de serviço para não
        // depender só do Controller nem do que o Frontend deixa de listar.
        if (trialCampaignAdminService.isFreePlanVersionModule(req.planVersionModuleId())) {
            auditService.log(userId, "trial_campaign.creation_denied", "trial_campaigns",
                req.planVersionModuleId(),
                Map.of(
                    "reason", "Free plans do not support Trial campaigns.",
                    "planVersionModuleId", req.planVersionModuleId()
                ));
            throw new BadRequestException("Não é permitido criar campanhas Trial para o plano Free.");
        }

        UUID id = UUID.randomUUID();
        em.createNativeQuery(
            "INSERT INTO trial_campaigns " +
            "(id, plan_version_module_id, name, status, days, max_slots, start_date, end_date, notes, priority, created_by_user_id) " +
            "VALUES (:id, CAST(:pvmId AS uuid), :name, :status, :days, :maxSlots, " +
            "CAST(:startDate AS date), CAST(:endDate AS date), :notes, :priority, CAST(:userId AS uuid))"
        )
            .setParameter("id", id)
            .setParameter("pvmId", req.planVersionModuleId())
            .setParameter("name", req.name())
            .setParameter("status", status)
            .setParameter("days", req.days())
            .setParameter("maxSlots", req.maxSlots())
            .setParameter("startDate", req.startDate())
            .setParameter("endDate", req.endDate())
            .setParameter("notes", req.notes())
            .setParameter("priority", req.priority() != null ? req.priority() : 0)
            .setParameter("userId", userId)
            .executeUpdate();

        auditService.log(userId, "trial_campaign.created", "trial_campaigns", id.toString(),
            Map.of("name", req.name(), "status", status, "days", req.days(), "maxSlots", req.maxSlots()));

        return Response.ok(Map.of("id", id.toString(), "created", true)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
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

        String oldStatus;
        try {
            oldStatus = (String) em.createNativeQuery(
                "SELECT status FROM trial_campaigns WHERE id::text = :id"
            ).setParameter("id", id).getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("Campanha de Trial não encontrada");
        }

        // CANCELLED só é alcançável pela ação dedicada POST /{id}/cancel — mas se a
        // campanha já está CANCELLED, a edição pode seguir mexendo em outros campos
        // (notas, datas) sem reverter/reafirmar o cancelamento por aqui.
        String status;
        if ("CANCELLED".equals(oldStatus)) {
            if (!"CANCELLED".equals(req.status()))
                throw new BadRequestException("Campanha cancelada não pode ter o status alterado por edição");
            status = "CANCELLED";
        } else {
            status = validateStatus(req.status());
        }
        validateDates(req.startDate(), req.endDate());

        boolean hasParticipants = hasParticipants(id);
        boolean termsLocked = hasParticipants;
        if (hasParticipants) {
            // Termos que afetariam retroativamente quem já participou não podem mudar —
            // dias e vagas ficam congelados; para alterá-los, crie uma nova campanha.
            em.createNativeQuery(
                "UPDATE trial_campaigns SET " +
                "status = :status, start_date = CAST(:startDate AS date), end_date = CAST(:endDate AS date), " +
                "notes = :notes, priority = :priority, updated_at = NOW(), updated_by_user_id = CAST(:userId AS uuid) " +
                "WHERE id::text = :id"
            )
                .setParameter("status", status)
                .setParameter("startDate", req.startDate())
                .setParameter("endDate", req.endDate())
                .setParameter("notes", req.notes())
                .setParameter("priority", req.priority() != null ? req.priority() : 0)
                .setParameter("userId", userId)
                .setParameter("id", id)
                .executeUpdate();
        } else {
            validateTerms(req.days(), req.maxSlots());
            em.createNativeQuery(
                "UPDATE trial_campaigns SET " +
                "name = :name, status = :status, days = :days, max_slots = :maxSlots, " +
                "start_date = CAST(:startDate AS date), end_date = CAST(:endDate AS date), " +
                "notes = :notes, priority = :priority, updated_at = NOW(), updated_by_user_id = CAST(:userId AS uuid) " +
                "WHERE id::text = :id"
            )
                .setParameter("name", req.name())
                .setParameter("status", status)
                .setParameter("days", req.days())
                .setParameter("maxSlots", req.maxSlots())
                .setParameter("startDate", req.startDate())
                .setParameter("endDate", req.endDate())
                .setParameter("notes", req.notes())
                .setParameter("priority", req.priority() != null ? req.priority() : 0)
                .setParameter("userId", userId)
                .setParameter("id", id)
                .executeUpdate();
        }

        auditService.log(userId, "trial_campaign.updated", "trial_campaigns", id,
            Map.of("statusBefore", oldStatus, "statusAfter", status));

        return Response.ok(Map.of("id", id, "updated", true, "termsLocked", termsLocked)).build();
    }

    @POST
    @Path("/{id}/close")
    @Transactional
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

        int updated = em.createNativeQuery(
            "UPDATE trial_campaigns SET status = 'CLOSED', updated_at = NOW(), updated_by_user_id = CAST(:userId AS uuid) " +
            "WHERE id::text = :id"
        ).setParameter("userId", userId).setParameter("id", id).executeUpdate();

        if (updated == 0) throw new NotFoundException("Campanha de Trial não encontrada");

        auditService.log(userId, "trial_campaign.closed", "trial_campaigns", id, null);
        return Response.ok(Map.of("id", id, "status", "CLOSED")).build();
    }

    @POST
    @Path("/{id}/cancel")
    @Transactional
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
        String reason = (req != null && req.reason() != null && !req.reason().isBlank())
            ? req.reason() : "Cancelado manualmente pelo administrador.";

        // Cancelar apenas impede novas ativações — participantes que já iniciaram o
        // Trial continuam normalmente até trialEndAt (nenhuma outra tabela é tocada).
        int updated = em.createNativeQuery(
            "UPDATE trial_campaigns SET status = 'CANCELLED', cancelled_at = NOW(), cancel_reason = :reason, " +
            "updated_at = NOW(), updated_by_user_id = CAST(:userId AS uuid) " +
            "WHERE id::text = :id AND status IN ('ACTIVE', 'SCHEDULED')"
        ).setParameter("reason", reason).setParameter("userId", userId).setParameter("id", id).executeUpdate();

        if (updated == 0)
            return Response.status(404).entity(Map.of("error", "Campanha não encontrada ou não pode mais ser cancelada")).build();

        auditService.log(userId, "trial_campaign.cancelled", "trial_campaigns", id,
            Map.of("reason", reason));
        return Response.ok(Map.of("id", id, "status", "CANCELLED")).build();
    }

    public record CancelRequest(String reason) {}

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean hasParticipants(String campaignId) {
        long count = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM module_trial_history WHERE trial_campaign_id::text = :id"
        ).setParameter("id", campaignId).getSingleResult()).longValue();
        return count > 0;
    }

    private void validateTerms(Integer days, Integer maxSlots) {
        if (days == null || days < 1 || days > 365)
            throw new BadRequestException("days deve estar entre 1 e 365");
        if (maxSlots == null || maxSlots < 1)
            throw new BadRequestException("maxSlots deve ser maior ou igual a 1");
    }

    private String validateStatus(String status) {
        if (status == null || !VALID_REQUEST_STATUSES.contains(status))
            throw new BadRequestException(
                "status inválido: deve ser um de " + VALID_REQUEST_STATUSES +
                " (cancelamento é feito via POST /{id}/cancel)");
        return status;
    }

    private void validateDates(String startDate, String endDate) {
        if (startDate == null || endDate == null) return;
        try {
            if (LocalDate.parse(endDate).isBefore(LocalDate.parse(startDate)))
                throw new BadRequestException("endDate não pode ser anterior a startDate");
        } catch (java.time.format.DateTimeParseException e) {
            throw new BadRequestException("Data inválida: " + e.getMessage());
        }
    }

    private static Map<String, Object> mapRow(Object[] row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                   row[0]);
        m.put("planVersionModuleId",  row[1]);
        m.put("moduleId",             row[2]);
        m.put("moduleName",           row[3]);
        m.put("name",                 row[4]);
        m.put("status",               row[5]);
        m.put("days",                 row[6] != null ? ((Number) row[6]).intValue() : null);
        m.put("maxSlots",             row[7] != null ? ((Number) row[7]).intValue() : null);
        m.put("usedSlots",            row[8] != null ? ((Number) row[8]).intValue() : null);
        m.put("startDate",            row[9]);
        m.put("endDate",              row[10]);
        m.put("notes",                row[11]);
        m.put("priority",             row[12] != null ? ((Number) row[12]).intValue() : null);
        m.put("createdAt",            row[13]);
        m.put("updatedAt",            row[14]);
        m.put("createdByUserId",      row[15]);
        m.put("createdByName",        row[16]);
        m.put("updatedByUserId",      row[17]);
        m.put("updatedByName",        row[18]);
        m.put("expired",              Boolean.TRUE.equals(row[19]));
        return m;
    }
}
