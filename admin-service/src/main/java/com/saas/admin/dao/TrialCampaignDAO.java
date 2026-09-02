package com.saas.admin.dao;

import com.saas.admin.dto.TrialCampaignDTO;
import com.saas.admin.dto.TrialCampaignDetailDTO;
import com.saas.admin.dto.TrialCampaignHistoryEntryDTO;
import com.saas.admin.dto.TrialCampaignListItemDTO;
import com.saas.admin.dto.TrialCampaignParticipantDTO;
import com.saas.admin.dto.TrialCampaignRequest;
import com.saas.admin.to.StatusCountTO;
import com.saas.admin.to.TrialCampaignBaseTO;
import com.saas.admin.to.TrialCampaignDetailTO;
import com.saas.admin.to.TrialCampaignHistoryEntryTO;
import com.saas.admin.to.TrialCampaignListItemTO;
import com.saas.admin.to.TrialCampaignParticipantTO;
import com.saas.platformdatabase.query.DatabaseQuery;
import com.saas.platformdatabase.query.NativeQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TrialCampaignDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    // Colunas comuns a listByPlan/listAll/getDetail — mantidas em um só lugar para
    // não deixar os três SELECTs divergirem ao adicionar campos. Cada coluna tem alias
    // explicito porque tc/pm/cu/uu repetem nomes de coluna entre si (id, name, full_name).
    private static final String COMMON_COLUMNS =
        "tc.id::text AS id, tc.plan_version_module_id::text AS plan_version_module_id, " +
        "pm.id::text AS module_id, pm.name AS module_name, " +
        "tc.name AS name, tc.status AS status, tc.days AS days, tc.max_slots AS max_slots, tc.used_slots AS used_slots, " +
        "tc.start_date::text AS start_date, tc.end_date::text AS end_date, tc.notes AS notes, tc.priority AS priority, " +
        "tc.created_at::text AS created_at, tc.updated_at::text AS updated_at, " +
        "tc.created_by_user_id::text AS created_by_user_id, cu.full_name AS created_by_name, " +
        "tc.updated_by_user_id::text AS updated_by_user_id, uu.full_name AS updated_by_name, " +
        "(tc.status = 'CLOSED' AND tc.end_date IS NOT NULL AND tc.end_date < CURRENT_DATE) AS expired ";

    private static final String COMMON_JOINS =
        "FROM trial_campaigns tc " +
        "JOIN plan_version_modules pvm ON pvm.id = tc.plan_version_module_id " +
        "JOIN platform_modules pm ON pm.id = pvm.module_id " +
        "LEFT JOIN user_profiles cu ON cu.id = tc.created_by_user_id " +
        "LEFT JOIN user_profiles uu ON uu.id = tc.updated_by_user_id ";

    private static TrialCampaignDTO toDTO(TrialCampaignBaseTO row) {
        return new TrialCampaignDTO(
            row.id(), row.planVersionModuleId(), row.moduleId(), row.moduleName(),
            row.name(), row.status(), row.days(), row.maxSlots(), row.usedSlots(),
            row.startDate(), row.endDate(), row.notes(), row.priority(),
            row.createdAt(), row.updatedAt(), row.createdByUserId(), row.createdByName(),
            row.updatedByUserId(), row.updatedByName(), row.expired());
    }

    public List<TrialCampaignDTO> findByPlan(String planId) {
        List<TrialCampaignBaseTO> rows = databaseQuery
                .nativeQuery(em, "SELECT " + COMMON_COLUMNS + COMMON_JOINS +
                        "WHERE pvm.plan_id::text = :planId " +
                        "ORDER BY pm.name, tc.priority DESC, tc.created_at DESC",
                        TrialCampaignBaseTO.class)
                .setParameter("planId", planId)
                .getResultList();

        return rows.stream().map(TrialCampaignDAO::toDTO).toList();
    }

    public record ListFilters(
        String status, String moduleId, String planId, String createdBy, String search,
        String startDateFrom, String startDateTo, Boolean hasSlots,
        String sortBy, String sortDir, int page, int size) {
    }

    public TrialCampaignListResult listAll(ListFilters f) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1 ");
        Map<String, Object> params = new LinkedHashMap<>();
        if (f.status() != null && !f.status().isBlank()) {
            where.append("AND tc.status = :status ");
            params.put("status", f.status());
        }
        if (f.moduleId() != null && !f.moduleId().isBlank()) {
            where.append("AND pm.id::text = :moduleId ");
            params.put("moduleId", f.moduleId());
        }
        if (f.planId() != null && !f.planId().isBlank()) {
            where.append("AND pvm.plan_id::text = :planId ");
            params.put("planId", f.planId());
        }
        if (f.createdBy() != null && !f.createdBy().isBlank()) {
            where.append("AND tc.created_by_user_id::text = :createdBy ");
            params.put("createdBy", f.createdBy());
        }
        if (f.search() != null && !f.search().isBlank()) {
            where.append("AND tc.name ILIKE :search ");
            params.put("search", "%" + f.search() + "%");
        }
        if (f.startDateFrom() != null && !f.startDateFrom().isBlank()) {
            where.append("AND tc.start_date >= CAST(:startDateFrom AS date) ");
            params.put("startDateFrom", f.startDateFrom());
        }
        if (f.startDateTo() != null && !f.startDateTo().isBlank()) {
            where.append("AND tc.start_date <= CAST(:startDateTo AS date) ");
            params.put("startDateTo", f.startDateTo());
        }
        if (Boolean.TRUE.equals(f.hasSlots())) {
            where.append("AND tc.used_slots < tc.max_slots ");
        }

        String orderColumn = switch (f.sortBy() == null ? "" : f.sortBy()) {
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
        if (f.sortDir() != null && !f.sortDir().isBlank()) {
            orderDir = "desc".equalsIgnoreCase(f.sortDir()) ? "DESC" : "ASC";
        } else {
            orderDir = f.sortBy() == null ? "DESC" : "ASC";
        }

        Query countQuery = em.createNativeQuery(
            "SELECT COUNT(*) " + COMMON_JOINS + "JOIN plans p ON p.id = pvm.plan_id " + where
        );
        params.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        int offset = (f.page() - 1) * f.size();
        NativeQuery<TrialCampaignListItemTO> dataQuery = databaseQuery.nativeQuery(em,
            "SELECT " + COMMON_COLUMNS + ", p.name AS plan_name, p.code AS plan_code, p.version AS plan_version " +
            COMMON_JOINS + "JOIN plans p ON p.id = pvm.plan_id " + where +
            "ORDER BY " + orderColumn + " " + orderDir + ", tc.id " +
            "LIMIT :limit OFFSET :offset",
            TrialCampaignListItemTO.class
        );
        params.forEach(dataQuery::setParameter);
        dataQuery.setParameter("limit", f.size());
        dataQuery.setParameter("offset", offset);
        List<TrialCampaignListItemTO> rows = dataQuery.getResultList();

        List<TrialCampaignListItemDTO> items = rows.stream().map(row -> new TrialCampaignListItemDTO(
            row.id(), row.planVersionModuleId(), row.moduleId(), row.moduleName(),
            row.name(), row.status(), row.days(), row.maxSlots(), row.usedSlots(),
            row.startDate(), row.endDate(), row.notes(), row.priority(),
            row.createdAt(), row.updatedAt(), row.createdByUserId(), row.createdByName(),
            row.updatedByUserId(), row.updatedByName(), row.expired(),
            row.planName(), row.planCode(), row.planVersion()
        )).toList();

        return new TrialCampaignListResult(items, total);
    }

    public record TrialCampaignListResult(List<TrialCampaignListItemDTO> items, long total) {
    }

    public Optional<TrialCampaignDetailDTO> findDetail(String id) {
        TrialCampaignDetailTO row = databaseQuery
                .nativeQuery(em, "SELECT " + COMMON_COLUMNS +
                        ", pm.slug AS module_slug, pm.icon_path AS module_icon, p.name AS plan_name, " +
                        "p.code AS plan_code, p.version AS plan_version, pvm.monthly_price AS plan_monthly_price, " +
                        "pvm.annual_monthly_price AS plan_annual_price " +
                        COMMON_JOINS + "JOIN plans p ON p.id = pvm.plan_id " +
                        "WHERE tc.id::text = :id",
                        TrialCampaignDetailTO.class)
                .setParameter("id", id)
                .getOptionalResult()
                .orElse(null);

        if (row == null) return Optional.empty();

        long totalParticipants = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM module_trial_history WHERE trial_campaign_id::text = :id"
        ).setParameter("id", id).getSingleResult()).longValue();

        long converted = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM module_trial_history WHERE trial_campaign_id::text = :id AND became_customer = TRUE"
        ).setParameter("id", id).getSingleResult()).longValue();

        double conversionPercent = totalParticipants == 0 ? 0.0 : (converted * 100.0 / totalParticipants);
        conversionPercent = Math.round(conversionPercent * 10.0) / 10.0;

        List<StatusCountTO> statusCounts = databaseQuery
                .nativeQuery(em, """
                        SELECT pms.status, COUNT(*)
                        FROM profile_module_subscriptions pms
                        JOIN module_trial_history h ON h.id = pms.trial_history_id
                        WHERE h.trial_campaign_id::text = :id
                        GROUP BY pms.status
                        """, StatusCountTO.class)
                .setParameter("id", id)
                .getResultList();

        long active = 0, expiredCount = 0, cancelledCount = 0;
        for (StatusCountTO sc : statusCounts) {
            String st = sc.status();
            long count = sc.count();
            if ("TRIAL".equals(st)) active += count;
            else if ("EXPIRED".equals(st)) expiredCount += count;
            else if ("TRIAL_CANCELLED".equals(st) || "CANCELED".equals(st)) cancelledCount += count;
        }

        return Optional.of(new TrialCampaignDetailDTO(
            row.id(), row.planVersionModuleId(), row.moduleId(), row.moduleName(),
            row.name(), row.status(), row.days(), row.maxSlots(), row.usedSlots(),
            row.startDate(), row.endDate(), row.notes(), row.priority(),
            row.createdAt(), row.updatedAt(), row.createdByUserId(), row.createdByName(),
            row.updatedByUserId(), row.updatedByName(), row.expired(),
            row.moduleSlug(), row.moduleIcon(), row.planName(), row.planCode(), row.planVersion(),
            row.planMonthlyPrice(), row.planAnnualPrice(),
            totalParticipants, conversionPercent, active, expiredCount, cancelledCount));
    }

    public List<TrialCampaignParticipantDTO> findParticipants(String id) {
        List<TrialCampaignParticipantTO> rows = databaseQuery
                .nativeQuery(em, """
                        SELECT t.id::text, t.name, t.type, up.full_name, au.email,
                        h.trial_started_at::text, h.trial_finished_at::text, h.trial_canceled_at::text,
                        pms.status, h.became_customer
                        FROM module_trial_history h
                        JOIN tenants t ON t.id = h.tenant_id
                        LEFT JOIN user_profiles up ON up.id = h.started_by_user_id
                        LEFT JOIN auth.users au ON au.id = h.started_by_user_id
                        LEFT JOIN profile_module_subscriptions pms ON pms.trial_history_id = h.id
                        WHERE h.trial_campaign_id::text = :id
                        ORDER BY h.trial_started_at DESC
                        """, TrialCampaignParticipantTO.class)
                .setParameter("id", id)
                .getResultList();

        return rows.stream().map(row -> new TrialCampaignParticipantDTO(
            row.tenantId(), row.tenantName(),
            "individual".equals(row.tenantType()) ? "INDIVIDUAL" : "COMPANY",
            row.fullName(), row.email(), row.trialStartedAt(), row.trialFinishedAt(), row.trialCanceledAt(),
            row.subscriptionStatus(), Boolean.TRUE.equals(row.becameCustomer())
        )).toList();
    }

    public List<TrialCampaignHistoryEntryDTO> findHistory(String id) {
        List<TrialCampaignHistoryEntryTO> rows = databaseQuery
                .nativeQuery(em, """
                        SELECT al.action, up.full_name AS actor_name, al.created_at::text
                        FROM admin_audit_logs al
                        LEFT JOIN user_profiles up ON up.id = al.actor_user_id
                        WHERE al.resource = 'trial_campaigns' AND al.resource_id = :id
                        ORDER BY al.created_at ASC
                        """, TrialCampaignHistoryEntryTO.class)
                .setParameter("id", id)
                .getResultList();

        return rows.stream()
            .map(row -> new TrialCampaignHistoryEntryDTO(row.action(), row.actorName(), row.createdAt()))
            .toList();
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    public long countPvm(String pvmId) {
        return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM plan_version_modules WHERE id::text = :id"
        ).setParameter("id", pvmId).getSingleResult()).longValue();
    }

    public UUID insert(TrialCampaignRequest req, String status, String userId) {
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
        return id;
    }

    public Optional<String> findStatus(String id) {
        try {
            return Optional.of((String) em.createNativeQuery(
                "SELECT status FROM trial_campaigns WHERE id::text = :id"
            ).setParameter("id", id).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public boolean hasParticipants(String campaignId) {
        long count = ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM module_trial_history WHERE trial_campaign_id::text = :id"
        ).setParameter("id", campaignId).getSingleResult()).longValue();
        return count > 0;
    }

    public void updateLocked(String id, String status, TrialCampaignRequest req, String userId) {
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
    }

    public void updateFull(String id, String status, TrialCampaignRequest req, String userId) {
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

    public int close(String id, String userId) {
        return em.createNativeQuery(
            "UPDATE trial_campaigns SET status = 'CLOSED', updated_at = NOW(), updated_by_user_id = CAST(:userId AS uuid) " +
            "WHERE id::text = :id"
        ).setParameter("userId", userId).setParameter("id", id).executeUpdate();
    }

    public int cancel(String id, String reason, String userId) {
        return em.createNativeQuery(
            "UPDATE trial_campaigns SET status = 'CANCELLED', cancelled_at = NOW(), cancel_reason = :reason, " +
            "updated_at = NOW(), updated_by_user_id = CAST(:userId AS uuid) " +
            "WHERE id::text = :id AND status IN ('ACTIVE', 'SCHEDULED')"
        ).setParameter("reason", reason).setParameter("userId", userId).setParameter("id", id).executeUpdate();
    }

    // ─── Usado por AdminPlanNegocioImpl ao gerar uma nova versão de plano ─────

    public boolean isFreePlanVersionModule(String planVersionModuleId) {
        Number count = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM plan_version_modules pvm " +
            "JOIN plans p ON p.id = pvm.plan_id " +
            "WHERE pvm.id::text = :id AND p.code = 'free'"
        ).setParameter("id", planVersionModuleId).getSingleResult();
        return count.longValue() > 0;
    }

    @SuppressWarnings("unchecked")
    public List<String> findActiveOrScheduledCampaignIds(String oldPlanId) {
        return em.createNativeQuery(
            "SELECT tc.id::text FROM trial_campaigns tc " +
            "JOIN plan_version_modules pvm ON pvm.id = tc.plan_version_module_id " +
            "WHERE pvm.plan_id::text = :oldPlanId AND tc.status IN ('ACTIVE', 'SCHEDULED')"
        ).setParameter("oldPlanId", oldPlanId).getResultList();
    }

    public void bulkCancel(List<String> ids, String reason, UUID actorUserId) {
        em.createNativeQuery(
            "UPDATE trial_campaigns SET status = 'CANCELLED', cancelled_at = NOW(), cancel_reason = :reason, " +
            "updated_at = NOW(), updated_by_user_id = :actorUserId " +
            "WHERE id::text IN (:ids)"
        )
            .setParameter("reason", reason)
            .setParameter("actorUserId", actorUserId)
            .setParameter("ids", ids)
            .executeUpdate();
    }
}
