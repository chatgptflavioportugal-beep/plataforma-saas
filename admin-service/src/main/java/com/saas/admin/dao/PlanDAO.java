package com.saas.admin.dao;

import com.saas.admin.dto.PlanRequest;
import com.saas.admin.dto.PlanVersionHistoryDTO;
import com.saas.admin.dto.PlanVersionModuleDTO;
import com.saas.admin.dto.PlanVersionModuleLimitRequest;
import com.saas.admin.dto.PlanVersionModuleRequest;
import com.saas.admin.dto.PlanSummaryDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a dados de plans / plan_version_modules / plan_version_module_limits.
 * Toda regra de negócio (validações, checagem de assinantes, cópia de versão)
 * fica em {@link com.saas.admin.negocio.AdminPlanNegocioImpl} — esta classe só
 * executa SQL.
 */
@ApplicationScoped
public class PlanDAO {

    @Inject
    EntityManager em;

    // ----------------------------------------------------------------
    // Expressões SQL reutilizáveis para cálculo de totais pelos módulos
    // ----------------------------------------------------------------

    private static final String TOTAL_MONTHLY_EXPR =
        "COALESCE((SELECT SUM(pvm.monthly_price) FROM plan_version_modules pvm " +
        " WHERE pvm.plan_id = p.id AND pvm.status = 'active'), 0)";

    private static final String TOTAL_ANNUAL_MONTHLY_EXPR =
        "COALESCE((SELECT SUM(pvm.annual_monthly_price) FROM plan_version_modules pvm " +
        " WHERE pvm.plan_id = p.id AND pvm.status = 'active'), 0)";

    private static final String MODULE_COUNT_EXPR =
        "(SELECT COUNT(*) FROM plan_version_modules pvm " +
        " WHERE pvm.plan_id = p.id AND pvm.status = 'active')::int";

    private static final String MODULES_DETAIL_JSON_EXPR =
        "COALESCE((SELECT json_agg(json_build_object(" +
        "  'id', pvm.id::text," +
        "  'module_id', pvm.module_id::text," +
        "  'module_name', pm.name," +
        "  'module_slug', pm.slug," +
        "  'module_icon_path', pm.icon_path," +
        "  'monthly_price', pvm.monthly_price," +
        "  'annual_monthly_price', pvm.annual_monthly_price," +
        "  'status', pvm.status," +
        "  'sort_order', pvm.sort_order," +
        "  'limits', COALESCE((" +
        "    SELECT json_agg(json_build_object(" +
        "      'id', pvml.id::text," +
        "      'title', pvml.title," +
        "      'description', pvml.description," +
        "      'code', pvml.code," +
        "      'limit_value', pvml.limit_value," +
        "      'unit', pvml.unit," +
        "      'sort_order', pvml.sort_order" +
        "    ) ORDER BY pvml.sort_order)" +
        "    FROM plan_version_module_limits pvml" +
        "    WHERE pvml.plan_version_module_id = pvm.id" +
        "  ), '[]'::json)" +
        ") ORDER BY pvm.sort_order, pm.name)" +
        " FROM plan_version_modules pvm" +
        " JOIN platform_modules pm ON pm.id = pvm.module_id" +
        " WHERE pvm.plan_id = p.id), '[]'::json)::text";

    private static final String PAID_SUBS_EXPR =
        "COALESCE((SELECT COUNT(*) FROM profile_module_subscriptions pms " +
        " JOIN plan_version_modules pvm2 ON pvm2.id = pms.plan_version_id " +
        " WHERE pvm2.plan_id = p.id AND pms.status = 'ACTIVE'), 0)";

    private static final String TRIAL_SUBS_EXPR =
        "COALESCE((SELECT COUNT(*) FROM profile_module_subscriptions pms " +
        " JOIN plan_version_modules pvm2 ON pvm2.id = pms.plan_version_id " +
        " WHERE pvm2.plan_id = p.id AND pms.status IN ('TRIAL', 'TRIAL_CANCELLED')), 0)";

    private static final String TRIAL_CAMPAIGNS_ACTIVE_EXPR =
        "COALESCE((SELECT COUNT(*) FROM trial_campaigns tc " +
        " JOIN plan_version_modules pvm3 ON pvm3.id = tc.plan_version_module_id " +
        " WHERE pvm3.plan_id = p.id AND tc.status IN ('ACTIVE', 'SCHEDULED')), 0)";

    private static final String TRIAL_CAMPAIGNS_CANCELLED_EXPR =
        "COALESCE((SELECT COUNT(*) FROM trial_campaigns tc " +
        " JOIN plan_version_modules pvm3 ON pvm3.id = tc.plan_version_module_id " +
        " WHERE pvm3.plan_id = p.id AND tc.status = 'CANCELLED'), 0)";

    // ----------------------------------------------------------------
    // Snapshot interno da versão atual de um plano (DAO -> Negocio apenas)
    // ----------------------------------------------------------------

    public record CurrentPlanRow(
            String code, int maxUsers, int maxAiRequestsMonth, int version,
            String name, String description, int sortOrder, String billingType,
            int discountAnnualPercent, boolean isMostPopular, String planType) {
    }

    public record PlanActiveFlags(boolean isActive, boolean isMostPopular) {
    }

    public record PlanPopularEligibility(boolean isActive, boolean isCurrentVersion) {
    }

    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<PlanSummaryDTO> findAllPlansAdmin() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT p.id::text, p.name, p.code, p.description, " +
                "p.price_monthly, p.price_annual, p.discount_annual_percent, " +
                "p.max_users, p.max_ai_requests_month, " +
                "p.is_active, p.sort_order, p.version, p.is_current_version, " +
                "p.parent_plan_id::text, p.billing_type, p.created_at::text, " +
                "p.is_most_popular, p.plan_type, " +
                PAID_SUBS_EXPR + " AS paid_subscriptions, " +
                TRIAL_SUBS_EXPR + " AS trial_subscriptions, " +
                TOTAL_MONTHLY_EXPR + " AS total_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " AS total_annual_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " * 12 AS total_annual_price, " +
                MODULE_COUNT_EXPR + " AS module_count " +
                "FROM plans p " +
                "ORDER BY p.plan_type, p.code, p.version"
        ).getResultList();

        return rows.stream().map(row -> {
            long paidSubs  = ((Number) row[18]).longValue();
            long trialSubs = ((Number) row[19]).longValue();
            return new PlanSummaryDTO(
                (String) row[0], (String) row[1], (String) row[2], (String) row[3],
                (BigDecimal) row[4], (BigDecimal) row[5], ((Number) row[6]).intValue(),
                (Integer) row[7], (Integer) row[8], (Boolean) row[9], (Integer) row[10],
                (Integer) row[11], (Boolean) row[12], (String) row[13], (String) row[14],
                (String) row[15], (Boolean) row[16], (String) row[17],
                paidSubs, trialSubs, paidSubs + trialSubs,
                (BigDecimal) row[20], (BigDecimal) row[21], (BigDecimal) row[22],
                ((Number) row[23]).intValue());
        }).toList();
    }

    @SuppressWarnings("unchecked")
    public List<PlanVersionHistoryDTO> findVersionHistory(String planCode) {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT p.id::text, p.name, p.code, p.description, " +
                "p.price_monthly, p.price_annual, p.discount_annual_percent, " +
                "p.max_users, p.max_ai_requests_month, " +
                "p.is_active, p.sort_order, p.version, p.is_current_version, " +
                "p.is_most_popular, p.created_at::text, p.plan_type, " +
                PAID_SUBS_EXPR + " AS paid_subscriptions, " +
                TRIAL_SUBS_EXPR + " AS trial_subscriptions, " +
                TOTAL_MONTHLY_EXPR + " AS total_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " AS total_annual_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " * 12 AS total_annual_price, " +
                MODULE_COUNT_EXPR + " AS module_count, " +
                "p.billing_type, " +
                TRIAL_CAMPAIGNS_ACTIVE_EXPR + " AS trial_campaigns_active, " +
                TRIAL_CAMPAIGNS_CANCELLED_EXPR + " AS trial_campaigns_cancelled, " +
                MODULES_DETAIL_JSON_EXPR + " AS modules_json " +
                "FROM plans p " +
                "WHERE p.code = :code " +
                "ORDER BY p.version DESC"
        ).setParameter("code", planCode).getResultList();

        return rows.stream().map(row -> {
            long paidSubs  = ((Number) row[16]).longValue();
            long trialSubs = ((Number) row[17]).longValue();
            return new PlanVersionHistoryDTO(
                (String) row[0], (String) row[1], (String) row[2], (String) row[3],
                (BigDecimal) row[4], (BigDecimal) row[5], ((Number) row[6]).intValue(),
                (Integer) row[7], (Integer) row[8], (Boolean) row[9], (Integer) row[10],
                (Integer) row[11], (Boolean) row[12], (Boolean) row[13], (String) row[14],
                (String) row[15], paidSubs, trialSubs, paidSubs + trialSubs,
                (BigDecimal) row[18], (BigDecimal) row[19], (BigDecimal) row[20],
                ((Number) row[21]).intValue(), (String) row[22],
                ((Number) row[23]).longValue(), ((Number) row[24]).longValue(), (String) row[25]);
        }).toList();
    }

    public UUID insertPlan(PlanRequest req) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO plans (id, code, name, description, price_monthly, price_annual, " +
                "discount_annual_percent, max_users, max_ai_requests_month, " +
                "is_active, sort_order, version, is_current_version, billing_type, is_most_popular, plan_type) " +
                "VALUES (:id, :code, :name, :description, :priceMonthly, :priceAnnual, " +
                ":discountAnnualPercent, :maxUsers, :maxAiRequestsMonth, " +
                "TRUE, :sortOrder, 1, TRUE, :billingType, FALSE, :planType)"
        )
        .setParameter("id", id)
        .setParameter("code", req.code())
        .setParameter("name", req.name())
        .setParameter("description", req.description())
        .setParameter("priceMonthly", BigDecimal.ZERO)
        .setParameter("priceAnnual", BigDecimal.ZERO)
        .setParameter("discountAnnualPercent", req.discountAnnualPercent() != null ? req.discountAnnualPercent() : 0)
        .setParameter("maxUsers", req.maxUsers() != null ? req.maxUsers() : 5)
        .setParameter("maxAiRequestsMonth", req.maxAiRequestsMonth() != null ? req.maxAiRequestsMonth() : 100)
        .setParameter("sortOrder", req.sortOrder() != null ? req.sortOrder() : 99)
        .setParameter("billingType", req.billingType() != null ? req.billingType() : "both")
        .setParameter("planType", req.planType() != null ? req.planType() : "business")
        .executeUpdate();
        return id;
    }

    @SuppressWarnings("unchecked")
    public Optional<CurrentPlanRow> fetchCurrentPlan(String planId) {
        try {
            Object[] r = (Object[]) em.createNativeQuery(
                    "SELECT code, max_users, max_ai_requests_month, " +
                    "version, name, description, sort_order, billing_type, " +
                    "discount_annual_percent, is_most_popular, plan_type " +
                    "FROM plans WHERE id::text = :id AND is_current_version = TRUE"
            ).setParameter("id", planId).getSingleResult();

            return Optional.of(new CurrentPlanRow(
                (String) r[0], ((Number) r[1]).intValue(), ((Number) r[2]).intValue(),
                ((Number) r[3]).intValue(), (String) r[4], (String) r[5],
                ((Number) r[6]).intValue(), (String) r[7], ((Number) r[8]).intValue(),
                (Boolean) r[9], (String) r[10]));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public void deactivateCurrentVersion(String planId) {
        em.createNativeQuery(
                "UPDATE plans SET is_current_version = FALSE, is_most_popular = FALSE, updated_at = NOW() " +
                "WHERE id::text = :id"
        ).setParameter("id", planId).executeUpdate();
    }

    public void insertNewVersion(UUID newId, String code, String name, String description,
            Integer discountAnnualPercent, Integer maxUsers, Integer maxAiRequestsMonth,
            Integer sortOrder, int version, String parentId, String billingType,
            boolean isMostPopular, String planType) {
        em.createNativeQuery(
                "INSERT INTO plans (id, code, name, description, price_monthly, price_annual, " +
                "discount_annual_percent, max_users, max_ai_requests_month, " +
                "is_active, sort_order, version, is_current_version, parent_plan_id, billing_type, is_most_popular, plan_type) " +
                "VALUES (:id, :code, :name, :description, 0, 0, " +
                ":discountAnnualPercent, :maxUsers, :maxAiRequestsMonth, " +
                "TRUE, :sortOrder, :version, TRUE, :parentId, :billingType, :isMostPopular, :planType)"
        )
        .setParameter("id", newId)
        .setParameter("code", code)
        .setParameter("name", name)
        .setParameter("description", description)
        .setParameter("discountAnnualPercent", discountAnnualPercent)
        .setParameter("maxUsers", maxUsers)
        .setParameter("maxAiRequestsMonth", maxAiRequestsMonth)
        .setParameter("sortOrder", sortOrder)
        .setParameter("version", version)
        .setParameter("parentId", UUID.fromString(parentId))
        .setParameter("billingType", billingType)
        .setParameter("isMostPopular", isMostPopular)
        .setParameter("planType", planType)
        .executeUpdate();
    }

    public void copyModulesToNewVersion(String oldPlanId, UUID newPlanId) {
        em.createNativeQuery(
                "INSERT INTO plan_version_modules " +
                "(plan_id, module_id, monthly_price, annual_monthly_price, status, sort_order) " +
                "SELECT CAST(:newPlanId AS uuid), module_id, monthly_price, annual_monthly_price, status, sort_order " +
                "FROM plan_version_modules WHERE plan_id::text = :oldPlanId"
        ).setParameter("newPlanId", newPlanId.toString()).setParameter("oldPlanId", oldPlanId).executeUpdate();

        em.createNativeQuery(
                "INSERT INTO plan_version_module_limits " +
                "(plan_version_module_id, title, description, code, limit_value, unit, sort_order) " +
                "SELECT new_pvm.id, old_l.title, old_l.description, old_l.code, old_l.limit_value, old_l.unit, old_l.sort_order " +
                "FROM plan_version_module_limits old_l " +
                "JOIN plan_version_modules old_pvm ON old_pvm.id = old_l.plan_version_module_id " +
                "JOIN plan_version_modules new_pvm " +
                "  ON new_pvm.plan_id = CAST(:newPlanId AS uuid) AND new_pvm.module_id = old_pvm.module_id " +
                "WHERE old_pvm.plan_id::text = :oldPlanId"
        ).setParameter("newPlanId", newPlanId.toString()).setParameter("oldPlanId", oldPlanId).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public Optional<PlanPopularEligibility> fetchPopularEligibility(String planId) {
        try {
            Object[] r = (Object[]) em.createNativeQuery(
                    "SELECT is_active, is_current_version FROM plans WHERE id::text = :id"
            ).setParameter("id", planId).getSingleResult();
            return Optional.of(new PlanPopularEligibility((Boolean) r[0], (Boolean) r[1]));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<PlanActiveFlags> fetchActiveFlags(String planId) {
        try {
            Object[] r = (Object[]) em.createNativeQuery(
                    "SELECT is_active, is_most_popular FROM plans WHERE id::text = :id"
            ).setParameter("id", planId).getSingleResult();
            return Optional.of(new PlanActiveFlags((Boolean) r[0], (Boolean) r[1]));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public int toggleActive(String planId) {
        return em.createNativeQuery(
                "UPDATE plans SET is_active = NOT is_active, updated_at = NOW() WHERE id::text = :id"
        ).setParameter("id", planId).executeUpdate();
    }

    public void clearMostPopular(String planId) {
        em.createNativeQuery("UPDATE plans SET is_most_popular = FALSE WHERE id::text = :id")
          .setParameter("id", planId).executeUpdate();
    }

    public void clearAllMostPopular() {
        em.createNativeQuery("UPDATE plans SET is_most_popular = FALSE WHERE is_most_popular = TRUE")
          .executeUpdate();
    }

    public void setMostPopular(String planId) {
        em.createNativeQuery(
                "UPDATE plans SET is_most_popular = TRUE, updated_at = NOW() WHERE id::text = :id"
        ).setParameter("id", planId).executeUpdate();
    }

    // ----------------------------------------------------------------
    // plan_version_modules
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<PlanVersionModuleDTO> findPlanVersionModules(String planId) {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT pvm.id::text, pvm.plan_id::text, pvm.module_id::text, " +
                "pm.name AS module_name, pm.slug AS module_slug, pm.icon_path AS module_icon_path, " +
                "pvm.monthly_price, pvm.annual_monthly_price, pvm.status, pvm.sort_order, " +
                "pvm.created_at::text, pvm.updated_at::text, " +
                "COALESCE((SELECT json_agg(json_build_object(" +
                "  'id', pvml.id::text, 'title', pvml.title, 'description', pvml.description, " +
                "  'code', pvml.code, 'limit_value', pvml.limit_value, " +
                "  'unit', pvml.unit, 'sort_order', pvml.sort_order" +
                ") ORDER BY pvml.sort_order) FROM plan_version_module_limits pvml " +
                "WHERE pvml.plan_version_module_id = pvm.id), '[]'::json)::text AS limits_json " +
                "FROM plan_version_modules pvm " +
                "JOIN platform_modules pm ON pm.id = pvm.module_id " +
                "WHERE pvm.plan_id::text = :planId " +
                "ORDER BY pvm.sort_order, pm.name"
        ).setParameter("planId", planId).getResultList();

        return rows.stream()
            .map(row -> new PlanVersionModuleDTO(
                (String) row[0], (String) row[1], (String) row[2], (String) row[3],
                (String) row[4], (String) row[5], (BigDecimal) row[6], (BigDecimal) row[7],
                (String) row[8], (Integer) row[9], (String) row[10], (String) row[11], (String) row[12]))
            .toList();
    }

    public long countPlans(String planId) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM plans WHERE id::text = :id"
        ).setParameter("id", planId).getSingleResult()).longValue();
    }

    public long countDuplicateModule(String planId, String moduleId) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM plan_version_modules WHERE plan_id::text = :planId AND module_id::text = :moduleId"
        ).setParameter("planId", planId).setParameter("moduleId", moduleId).getSingleResult()).longValue();
    }

    public UUID insertPlanVersionModule(String planId, PlanVersionModuleRequest req) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO plan_version_modules " +
                "(id, plan_id, module_id, monthly_price, annual_monthly_price, status, sort_order) " +
                "VALUES (:id, CAST(:planId AS uuid), CAST(:moduleId AS uuid), :monthlyPrice, :annualMonthlyPrice, :status, :sortOrder)"
        )
        .setParameter("id", id)
        .setParameter("planId", planId)
        .setParameter("moduleId", req.moduleId())
        .setParameter("monthlyPrice", req.monthlyPrice() != null ? req.monthlyPrice() : BigDecimal.ZERO)
        .setParameter("annualMonthlyPrice", req.annualMonthlyPrice() != null ? req.annualMonthlyPrice() : BigDecimal.ZERO)
        .setParameter("status", req.status() != null ? req.status() : "active")
        .setParameter("sortOrder", req.sortOrder() != null ? req.sortOrder() : 99)
        .executeUpdate();
        return id;
    }

    public void insertPlanVersionModule(UUID id, String planId, PlanVersionModuleRequest req) {
        em.createNativeQuery(
                "INSERT INTO plan_version_modules " +
                "(id, plan_id, module_id, monthly_price, annual_monthly_price, status, sort_order) " +
                "VALUES (:id, CAST(:planId AS uuid), CAST(:moduleId AS uuid), " +
                ":monthlyPrice, :annualMonthlyPrice, :status, :sortOrder)"
        )
        .setParameter("id", id)
        .setParameter("planId", planId)
        .setParameter("moduleId", req.moduleId())
        .setParameter("monthlyPrice", req.monthlyPrice() != null ? req.monthlyPrice() : BigDecimal.ZERO)
        .setParameter("annualMonthlyPrice", req.annualMonthlyPrice() != null ? req.annualMonthlyPrice() : BigDecimal.ZERO)
        .setParameter("status", req.status() != null ? req.status() : "active")
        .setParameter("sortOrder", req.sortOrder() != null ? req.sortOrder() : 99)
        .executeUpdate();
    }

    public int updatePlanVersionModule(String pvmId, PlanVersionModuleRequest req) {
        return em.createNativeQuery(
                "UPDATE plan_version_modules SET " +
                "monthly_price = :monthlyPrice, annual_monthly_price = :annualMonthlyPrice, " +
                "status = :status, sort_order = :sortOrder, updated_at = NOW() " +
                "WHERE id::text = :id"
        )
        .setParameter("monthlyPrice", req.monthlyPrice() != null ? req.monthlyPrice() : BigDecimal.ZERO)
        .setParameter("annualMonthlyPrice", req.annualMonthlyPrice() != null ? req.annualMonthlyPrice() : BigDecimal.ZERO)
        .setParameter("status", req.status() != null ? req.status() : "active")
        .setParameter("sortOrder", req.sortOrder() != null ? req.sortOrder() : 99)
        .setParameter("id", pvmId)
        .executeUpdate();
    }

    public int deletePlanVersionModule(String pvmId) {
        return em.createNativeQuery(
                "DELETE FROM plan_version_modules WHERE id::text = :id"
        ).setParameter("id", pvmId).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public Optional<String> findPlanIdForPvm(String pvmId) {
        try {
            return Optional.of((String) em.createNativeQuery(
                    "SELECT plan_id::text FROM plan_version_modules WHERE id::text = :id"
            ).setParameter("id", pvmId).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public long countSubscribers(String planId) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM tenant_subscriptions " +
                "WHERE plan_id::text = :planId AND status IN ('trial', 'active', 'past_due')"
        ).setParameter("planId", planId).getSingleResult()).longValue();
    }

    // ----------------------------------------------------------------
    // plan_version_module_limits
    // ----------------------------------------------------------------

    public long countPvm(String pvmId) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM plan_version_modules WHERE id::text = :id"
        ).setParameter("id", pvmId).getSingleResult()).longValue();
    }

    public UUID insertLimit(String pvmId, PlanVersionModuleLimitRequest req) {
        UUID id = UUID.randomUUID();
        insertLimit(id, pvmId, req);
        return id;
    }

    public void insertLimit(UUID id, String pvmId, PlanVersionModuleLimitRequest req) {
        em.createNativeQuery(
                "INSERT INTO plan_version_module_limits " +
                "(id, plan_version_module_id, title, description, code, limit_value, unit, sort_order) " +
                "VALUES (:id, CAST(:pvmId AS uuid), :title, :description, :code, :limitValue, :unit, :sortOrder)"
        )
        .setParameter("id", id)
        .setParameter("pvmId", pvmId)
        .setParameter("title", req.title())
        .setParameter("description", req.description())
        .setParameter("code", req.code())
        .setParameter("limitValue", req.limitValue())
        .setParameter("unit", req.unit())
        .setParameter("sortOrder", req.sortOrder() != null ? req.sortOrder() : 99)
        .executeUpdate();
    }

    public int updateLimit(String limitId, PlanVersionModuleLimitRequest req) {
        return em.createNativeQuery(
                "UPDATE plan_version_module_limits SET " +
                "title = :title, description = :description, code = :code, " +
                "limit_value = :limitValue, unit = :unit, sort_order = :sortOrder, updated_at = NOW() " +
                "WHERE id::text = :id"
        )
        .setParameter("title", req.title())
        .setParameter("description", req.description())
        .setParameter("code", req.code())
        .setParameter("limitValue", req.limitValue())
        .setParameter("unit", req.unit())
        .setParameter("sortOrder", req.sortOrder() != null ? req.sortOrder() : 99)
        .setParameter("id", limitId)
        .executeUpdate();
    }

    public int deleteLimit(String limitId) {
        return em.createNativeQuery(
                "DELETE FROM plan_version_module_limits WHERE id::text = :id"
        ).setParameter("id", limitId).executeUpdate();
    }
}
