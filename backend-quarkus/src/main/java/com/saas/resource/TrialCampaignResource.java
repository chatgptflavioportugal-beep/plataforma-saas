package com.saas.resource;

import com.saas.service.AuditService;
import com.saas.service.TrialCampaignService;
import io.quarkus.security.Authenticated;
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
 * (indicadores + participantes + histórico). Regras de elegibilidade/seleção em
 * tempo real ficam em TrialCampaignService; este recurso só cuida do ciclo de
 * vida administrativo das campanhas.
 */
@Path("/api/v1/admin/trial-campaigns")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TrialCampaignResource {

    // Status que podem ser atribuídos diretamente por criação/edição. CANCELLED só
    // é alcançável pela ação dedicada POST /{id}/cancel (confirmação + auditoria).
    private static final List<String> VALID_REQUEST_STATUSES = List.of("ACTIVE", "SCHEDULED", "CLOSED");

    @Inject EntityManager em;
    @Inject AdminResource adminResource;
    @Inject AuditService auditService;
    @Inject TrialCampaignService trialCampaignService;

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
    @SuppressWarnings("unchecked")
    public Response listByPlan(@PathParam("planId") String planId) {
        adminResource.requireAdminPermission("admin.trials.view");

        List<Object[]> rows = em.createNativeQuery(
            "SELECT " + COMMON_COLUMNS + COMMON_JOINS +
            "WHERE pvm.plan_id::text = :planId " +
            "ORDER BY pm.name, tc.priority DESC, tc.created_at DESC"
        ).setParameter("planId", planId).getResultList();

        return Response.ok(rows.stream().map(TrialCampaignResource::mapRow).collect(Collectors.toList())).build();
    }

    @GET
    @SuppressWarnings("unchecked")
    public Response listAll(
        @QueryParam("status") String status,
        @QueryParam("moduleId") String moduleId,
        @QueryParam("planId") String planId,
        @QueryParam("createdBy") String createdBy,
        @QueryParam("search") String search,
        @QueryParam("startDateFrom") String startDateFrom,
        @QueryParam("startDateTo") String startDateTo,
        @QueryParam("hasSlots") Boolean hasSlots,
        @QueryParam("sortBy") String sortBy,
        @QueryParam("sortDir") String sortDir,
        @QueryParam("page") Integer page,
        @QueryParam("size") Integer size
    ) {
        adminResource.requireAdminPermission("admin.trials.view");

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
    @SuppressWarnings("unchecked")
    public Response getDetail(@PathParam("id") String id) {
        adminResource.requireAdminPermission("admin.trials.view");

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
    @SuppressWarnings("unchecked")
    public Response listParticipants(@PathParam("id") String id) {
        adminResource.requireAdminPermission("admin.trials.view");

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
    @SuppressWarnings("unchecked")
    public Response history(@PathParam("id") String id) {
        adminResource.requireAdminPermission("admin.trials.view");

        List<Object[]> rows = em.createNativeQuery(
            "SELECT al.action, up.full_name, al.created_at::text " +
            "FROM audit_logs al " +
            "LEFT JOIN user_profiles up ON up.id = al.user_id " +
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
    public Response create(TrialCampaignRequest req) {
        adminResource.requireAdminPermission("admin.trials.create");
        String userId = adminResource.currentUserId();

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
        if (trialCampaignService.isFreePlanVersionModule(req.planVersionModuleId())) {
            auditService.log(null, UUID.fromString(userId), "trial_campaign.creation_denied", "trial_campaigns",
                req.planVersionModuleId(),
                Map.of(
                    "reason", "Free plans do not support Trial campaigns.",
                    "planVersionModuleId", req.planVersionModuleId()
                ), null);
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

        auditService.log(null, UUID.fromString(userId), "trial_campaign.created", "trial_campaigns", id.toString(),
            Map.of("name", req.name(), "status", status, "days", req.days(), "maxSlots", req.maxSlots()), null);

        return Response.ok(Map.of("id", id.toString(), "created", true)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") String id, TrialCampaignRequest req) {
        adminResource.requireAdminPermission("admin.trials.edit");
        String userId = adminResource.currentUserId();

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

        auditService.log(null, UUID.fromString(userId), "trial_campaign.updated", "trial_campaigns", id,
            Map.of("statusBefore", oldStatus, "statusAfter", status), null);

        return Response.ok(Map.of("id", id, "updated", true, "termsLocked", termsLocked)).build();
    }

    @POST
    @Path("/{id}/close")
    @Transactional
    public Response close(@PathParam("id") String id) {
        adminResource.requireAdminPermission("admin.trials.edit");
        String userId = adminResource.currentUserId();

        int updated = em.createNativeQuery(
            "UPDATE trial_campaigns SET status = 'CLOSED', updated_at = NOW(), updated_by_user_id = CAST(:userId AS uuid) " +
            "WHERE id::text = :id"
        ).setParameter("userId", userId).setParameter("id", id).executeUpdate();

        if (updated == 0) throw new NotFoundException("Campanha de Trial não encontrada");

        auditService.log(null, UUID.fromString(userId), "trial_campaign.closed", "trial_campaigns", id, null);
        return Response.ok(Map.of("id", id, "status", "CLOSED")).build();
    }

    @POST
    @Path("/{id}/cancel")
    @Transactional
    public Response cancel(@PathParam("id") String id, CancelRequest req) {
        adminResource.requireAdminPermission("admin.trials.cancel");
        String userId = adminResource.currentUserId();
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

        auditService.log(null, UUID.fromString(userId), "trial_campaign.cancelled", "trial_campaigns", id,
            Map.of("reason", reason), null);
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
