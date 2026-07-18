package com.saas.resource;

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
 * Administração de Trial Campaigns — CRUD e relatórios (indicadores + participantes).
 * Regras de elegibilidade/seleção em tempo real ficam em TrialCampaignService;
 * este recurso só cuida do ciclo de vida administrativo das campanhas.
 */
@Path("/api/v1/admin/trial-campaigns")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TrialCampaignResource {

    private static final List<String> VALID_STATUSES = List.of("ACTIVE", "SCHEDULED", "CLOSED", "CANCELLED");

    @Inject EntityManager em;
    @Inject AdminResource adminResource;

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

    // ─── Listagens ────────────────────────────────────────────────────────────

    @GET
    @Path("/by-plan/{planId}")
    @SuppressWarnings("unchecked")
    public Response listByPlan(@PathParam("planId") String planId) {
        adminResource.requireAdminPermission("admin.trials.view");

        List<Object[]> rows = em.createNativeQuery(
            "SELECT tc.id::text, tc.plan_version_module_id::text, pm.id::text, pm.name, " +
            "tc.name, tc.status, tc.days, tc.max_slots, tc.used_slots, " +
            "tc.start_date::text, tc.end_date::text, tc.notes, tc.priority, tc.created_at::text " +
            "FROM trial_campaigns tc " +
            "JOIN plan_version_modules pvm ON pvm.id = tc.plan_version_module_id " +
            "JOIN platform_modules pm ON pm.id = pvm.module_id " +
            "WHERE pvm.plan_id::text = :planId " +
            "ORDER BY pm.name, tc.priority DESC, tc.created_at DESC"
        ).setParameter("planId", planId).getResultList();

        return Response.ok(rows.stream().map(TrialCampaignResource::mapRow).collect(Collectors.toList())).build();
    }

    @GET
    @SuppressWarnings("unchecked")
    public Response listAll(@QueryParam("status") String status, @QueryParam("moduleId") String moduleId) {
        adminResource.requireAdminPermission("admin.trials.view");

        StringBuilder sql = new StringBuilder(
            "SELECT tc.id::text, tc.plan_version_module_id::text, pm.id::text, pm.name, " +
            "tc.name, tc.status, tc.days, tc.max_slots, tc.used_slots, " +
            "tc.start_date::text, tc.end_date::text, tc.notes, tc.priority, tc.created_at::text, " +
            "p.name AS plan_name, p.code AS plan_code, p.version AS plan_version " +
            "FROM trial_campaigns tc " +
            "JOIN plan_version_modules pvm ON pvm.id = tc.plan_version_module_id " +
            "JOIN platform_modules pm ON pm.id = pvm.module_id " +
            "JOIN plans p ON p.id = pvm.plan_id " +
            "WHERE 1 = 1"
        );
        Map<String, Object> params = new LinkedHashMap<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND tc.status = :status");
            params.put("status", status);
        }
        if (moduleId != null && !moduleId.isBlank()) {
            sql.append(" AND pm.id::text = :moduleId");
            params.put("moduleId", moduleId);
        }
        sql.append(" ORDER BY tc.created_at DESC");

        var query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<Object[]> rows = query.getResultList();

        return Response.ok(rows.stream().map(row -> {
            Map<String, Object> m = mapRow(row);
            m.put("planName", row[14]);
            m.put("planCode", row[15]);
            m.put("planVersion", row[16] != null ? ((Number) row[16]).intValue() : null);
            return m;
        }).collect(Collectors.toList())).build();
    }

    @GET
    @Path("/{id}")
    public Response getDetail(@PathParam("id") String id) {
        adminResource.requireAdminPermission("admin.trials.view");

        Object[] row;
        try {
            row = (Object[]) em.createNativeQuery(
                "SELECT tc.id::text, tc.plan_version_module_id::text, pm.id::text, pm.name, " +
                "tc.name, tc.status, tc.days, tc.max_slots, tc.used_slots, " +
                "tc.start_date::text, tc.end_date::text, tc.notes, tc.priority, tc.created_at::text " +
                "FROM trial_campaigns tc " +
                "JOIN plan_version_modules pvm ON pvm.id = tc.plan_version_module_id " +
                "JOIN platform_modules pm ON pm.id = pvm.module_id " +
                "WHERE tc.id::text = :id"
            ).setParameter("id", id).getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("Campanha de Trial não encontrada");
        }

        Map<String, Object> m = mapRow(row);

        long totalParticipants = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM module_trial_history WHERE trial_campaign_id::text = :id"
        ).setParameter("id", id).getSingleResult()).longValue();

        long converted = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM module_trial_history WHERE trial_campaign_id::text = :id AND became_customer = TRUE"
        ).setParameter("id", id).getSingleResult()).longValue();

        m.put("totalParticipants", totalParticipants);
        double conversionPercent = totalParticipants == 0 ? 0.0 : (converted * 100.0 / totalParticipants);
        m.put("conversionPercent", Math.round(conversionPercent * 10.0) / 10.0);

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

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    @POST
    @Transactional
    public Response create(TrialCampaignRequest req) {
        adminResource.requireAdminPermission("admin.trials.create");

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

        UUID id = UUID.randomUUID();
        em.createNativeQuery(
            "INSERT INTO trial_campaigns " +
            "(id, plan_version_module_id, name, status, days, max_slots, start_date, end_date, notes, priority) " +
            "VALUES (:id, CAST(:pvmId AS uuid), :name, :status, :days, :maxSlots, " +
            "CAST(:startDate AS date), CAST(:endDate AS date), :notes, :priority)"
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
            .executeUpdate();

        return Response.ok(Map.of("id", id.toString(), "created", true)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") String id, TrialCampaignRequest req) {
        adminResource.requireAdminPermission("admin.trials.edit");

        String status = validateStatus(req.status());
        validateDates(req.startDate(), req.endDate());

        boolean hasParticipants = hasParticipants(id);
        if (hasParticipants) {
            // Termos que afetariam retroativamente quem já participou não podem mudar —
            // dias e vagas ficam congelados; para alterá-los, crie uma nova campanha.
            int updated = em.createNativeQuery(
                "UPDATE trial_campaigns SET " +
                "status = :status, start_date = CAST(:startDate AS date), end_date = CAST(:endDate AS date), " +
                "notes = :notes, priority = :priority, updated_at = NOW() " +
                "WHERE id::text = :id"
            )
                .setParameter("status", status)
                .setParameter("startDate", req.startDate())
                .setParameter("endDate", req.endDate())
                .setParameter("notes", req.notes())
                .setParameter("priority", req.priority() != null ? req.priority() : 0)
                .setParameter("id", id)
                .executeUpdate();
            if (updated == 0) throw new NotFoundException("Campanha de Trial não encontrada");
            return Response.ok(Map.of("id", id, "updated", true, "termsLocked", true)).build();
        }

        validateTerms(req.days(), req.maxSlots());
        int updated = em.createNativeQuery(
            "UPDATE trial_campaigns SET " +
            "name = :name, status = :status, days = :days, max_slots = :maxSlots, " +
            "start_date = CAST(:startDate AS date), end_date = CAST(:endDate AS date), " +
            "notes = :notes, priority = :priority, updated_at = NOW() " +
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
            .setParameter("id", id)
            .executeUpdate();

        if (updated == 0) throw new NotFoundException("Campanha de Trial não encontrada");
        return Response.ok(Map.of("id", id, "updated", true, "termsLocked", false)).build();
    }

    @POST
    @Path("/{id}/close")
    @Transactional
    public Response close(@PathParam("id") String id) {
        adminResource.requireAdminPermission("admin.trials.edit");

        int updated = em.createNativeQuery(
            "UPDATE trial_campaigns SET status = 'CLOSED', updated_at = NOW() WHERE id::text = :id"
        ).setParameter("id", id).executeUpdate();

        if (updated == 0) throw new NotFoundException("Campanha de Trial não encontrada");
        return Response.ok(Map.of("id", id, "status", "CLOSED")).build();
    }

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
        if (status == null || !VALID_STATUSES.contains(status))
            throw new BadRequestException("status inválido: deve ser um de " + VALID_STATUSES);
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
        return m;
    }
}
