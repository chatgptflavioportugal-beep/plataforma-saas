package com.saas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class PlanService {

    @Inject
    EntityManager em;

    private static final Map<String, String> FEATURE_MIN_PLAN = Map.of(
            "pdf.merge", "free",
            "ai.agents", "starter",
            "reports.export", "pro",
            "api.access", "pro",
            "white_label", "enterprise"
    );

    public String getMinPlanCodeForFeature(String featureKey) {
        return FEATURE_MIN_PLAN.getOrDefault(featureKey, "enterprise");
    }

    // ----------------------------------------------------------------
    // Leitura pública: versões atuais ativas, com filtro opcional por tipo
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listActivePlans() {
        return listActivePlans(null);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listActivePlans(String planType) {
        String typeFilter = (planType != null && !planType.isBlank())
                ? " AND plan_type = '" + planType.replace("'", "''") + "'"
                : "";

        List<Object[]> rows = em.createNativeQuery(
                "SELECT id::text, name, code, description, price_monthly, price_annual, " +
                "discount_annual_percent, max_users, max_ai_requests_month, features::text, " +
                "sort_order, version, billing_type, is_most_popular, plan_type " +
                "FROM plans " +
                "WHERE is_active = TRUE AND is_current_version = TRUE" + typeFilter +
                " ORDER BY sort_order"
        ).getResultList();

        return rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            m.put("code", row[2]);
            m.put("description", row[3]);
            m.put("price_monthly", row[4]);
            m.put("price_annual", row[5]);
            m.put("discount_annual_percent", row[6]);
            m.put("max_users", row[7]);
            m.put("max_ai_requests_month", row[8]);
            m.put("features", row[9]);
            m.put("sort_order", row[10]);
            m.put("version", row[11]);
            m.put("billing_type", row[12]);
            m.put("is_most_popular", row[13]);
            m.put("plan_type", row[14]);
            return m;
        }).collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Admin: listagem completa com contagem de assinantes
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listAllPlansAdmin() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT p.id::text, p.name, p.code, p.description, " +
                "p.price_monthly, p.price_annual, p.discount_annual_percent, " +
                "p.max_users, p.max_ai_requests_month, p.features::text, " +
                "p.is_active, p.sort_order, p.version, p.is_current_version, " +
                "p.parent_plan_id::text, p.billing_type, p.created_at::text, " +
                "p.is_most_popular, p.plan_type, " +
                "COUNT(ts.id) AS subscriber_count " +
                "FROM plans p " +
                "LEFT JOIN tenant_subscriptions ts " +
                "  ON ts.plan_id = p.id AND ts.status IN ('trial', 'active', 'past_due') " +
                "GROUP BY p.id " +
                "ORDER BY p.plan_type, p.code, p.version"
        ).getResultList();

        return rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            m.put("code", row[2]);
            m.put("description", row[3]);
            m.put("price_monthly", row[4]);
            m.put("price_annual", row[5]);
            m.put("discount_annual_percent", row[6]);
            m.put("max_users", row[7]);
            m.put("max_ai_requests_month", row[8]);
            m.put("features", row[9]);
            m.put("is_active", row[10]);
            m.put("sort_order", row[11]);
            m.put("version", row[12]);
            m.put("is_current_version", row[13]);
            m.put("parent_plan_id", row[14]);
            m.put("billing_type", row[15]);
            m.put("created_at", row[16]);
            m.put("is_most_popular", row[17]);
            m.put("plan_type", row[18]);
            m.put("subscriber_count", ((Number) row[19]).longValue());
            return m;
        }).collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Admin: histórico de versões pelo code
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPlanVersionHistory(String planCode) {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT p.id::text, p.name, p.code, p.description, " +
                "p.price_monthly, p.price_annual, p.discount_annual_percent, " +
                "p.max_users, p.max_ai_requests_month, p.features::text, " +
                "p.is_active, p.sort_order, p.version, p.is_current_version, " +
                "p.is_most_popular, p.created_at::text, p.plan_type, " +
                "COUNT(ts.id) AS subscriber_count " +
                "FROM plans p " +
                "LEFT JOIN tenant_subscriptions ts " +
                "  ON ts.plan_id = p.id AND ts.status IN ('trial', 'active', 'past_due') " +
                "WHERE p.code = :code " +
                "GROUP BY p.id " +
                "ORDER BY p.version DESC"
        ).setParameter("code", planCode).getResultList();

        return rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            m.put("code", row[2]);
            m.put("description", row[3]);
            m.put("price_monthly", row[4]);
            m.put("price_annual", row[5]);
            m.put("discount_annual_percent", row[6]);
            m.put("max_users", row[7]);
            m.put("max_ai_requests_month", row[8]);
            m.put("features", row[9]);
            m.put("is_active", row[10]);
            m.put("sort_order", row[11]);
            m.put("version", row[12]);
            m.put("is_current_version", row[13]);
            m.put("is_most_popular", row[14]);
            m.put("created_at", row[15]);
            m.put("plan_type", row[16]);
            m.put("subscriber_count", ((Number) row[17]).longValue());
            return m;
        }).collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Admin: criar novo plano (v1, sem parent)
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> createPlan(PlanRequest req) {
        UUID id = UUID.randomUUID();

        em.createNativeQuery(
                "INSERT INTO plans (id, code, name, description, price_monthly, price_annual, " +
                "discount_annual_percent, max_users, max_ai_requests_month, features, " +
                "is_active, sort_order, version, is_current_version, billing_type, is_most_popular, plan_type) " +
                "VALUES (:id, :code, :name, :description, :priceMonthly, :priceAnnual, " +
                ":discountAnnualPercent, :maxUsers, :maxAiRequestsMonth, CAST(:features AS jsonb), " +
                "TRUE, :sortOrder, 1, TRUE, :billingType, FALSE, :planType)"
        )
        .setParameter("id", id)
        .setParameter("code", req.code())
        .setParameter("name", req.name())
        .setParameter("description", req.description())
        .setParameter("priceMonthly", req.priceMonthly())
        .setParameter("priceAnnual", req.priceAnnual())
        .setParameter("discountAnnualPercent", req.discountAnnualPercent() != null ? req.discountAnnualPercent() : 0)
        .setParameter("maxUsers", req.maxUsers())
        .setParameter("maxAiRequestsMonth", req.maxAiRequestsMonth())
        .setParameter("features", req.featuresJson())
        .setParameter("sortOrder", req.sortOrder() != null ? req.sortOrder() : 99)
        .setParameter("billingType", req.billingType() != null ? req.billingType() : "both")
        .setParameter("planType", req.planType() != null ? req.planType() : "business")
        .executeUpdate();

        return Map.of("id", id.toString(), "version", 1, "created", true);
    }

    // ----------------------------------------------------------------
    // Admin: gerar nova versão (preserva plan_type da versão anterior)
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> createNewVersion(String currentPlanId, PlanRequest req) {
        Object[] current = fetchCurrentPlan(currentPlanId);

        String code          = (String)  current[0];
        BigDecimal oldPriceM = (BigDecimal) current[1];
        BigDecimal oldPriceA = (BigDecimal) current[2];
        int oldMaxUsers      = ((Number) current[3]).intValue();
        int oldMaxAi         = ((Number) current[4]).intValue();
        String oldFeatures   = (String)  current[5];
        int oldVersion       = ((Number) current[6]).intValue();
        String oldName       = (String)  current[7];
        String oldDesc       = (String)  current[8];
        int oldSortOrder     = ((Number) current[9]).intValue();
        String oldBilling    = (String)  current[10];
        int oldDiscount      = ((Number) current[11]).intValue();
        boolean wasMostPop   = (Boolean) current[12];
        String oldPlanType   = (String)  current[13];

        em.createNativeQuery(
                "UPDATE plans SET is_current_version = FALSE, is_most_popular = FALSE, updated_at = NOW() " +
                "WHERE id::text = :id"
        ).setParameter("id", currentPlanId).executeUpdate();

        UUID newId = UUID.randomUUID();
        int newVersion = oldVersion + 1;

        em.createNativeQuery(
                "INSERT INTO plans (id, code, name, description, price_monthly, price_annual, " +
                "discount_annual_percent, max_users, max_ai_requests_month, features, " +
                "is_active, sort_order, version, is_current_version, parent_plan_id, billing_type, is_most_popular, plan_type) " +
                "VALUES (:id, :code, :name, :description, :priceMonthly, :priceAnnual, " +
                ":discountAnnualPercent, :maxUsers, :maxAiRequestsMonth, CAST(:features AS jsonb), " +
                "TRUE, :sortOrder, :version, TRUE, :parentId, :billingType, :isMostPopular, :planType)"
        )
        .setParameter("id", newId)
        .setParameter("code", code)
        .setParameter("name",              req.name() != null ? req.name() : oldName)
        .setParameter("description",       req.description() != null ? req.description() : oldDesc)
        .setParameter("priceMonthly",      req.priceMonthly() != null ? req.priceMonthly() : oldPriceM)
        .setParameter("priceAnnual",       req.priceAnnual()  != null ? req.priceAnnual()  : oldPriceA)
        .setParameter("discountAnnualPercent", req.discountAnnualPercent() != null ? req.discountAnnualPercent() : oldDiscount)
        .setParameter("maxUsers",          req.maxUsers() != null ? req.maxUsers() : oldMaxUsers)
        .setParameter("maxAiRequestsMonth",req.maxAiRequestsMonth() != null ? req.maxAiRequestsMonth() : oldMaxAi)
        .setParameter("features",          req.featuresJson() != null ? req.featuresJson() : oldFeatures)
        .setParameter("sortOrder",         req.sortOrder() != null ? req.sortOrder() : oldSortOrder)
        .setParameter("version",           newVersion)
        .setParameter("parentId",          UUID.fromString(currentPlanId))
        .setParameter("billingType",       req.billingType() != null ? req.billingType() : oldBilling)
        .setParameter("isMostPopular",     wasMostPop)
        .setParameter("planType",          req.planType() != null ? req.planType() : oldPlanType)
        .executeUpdate();

        return Map.of("id", newId.toString(), "version", newVersion, "new_version_created", true);
    }

    // ----------------------------------------------------------------
    // Admin: marcar como "Mais Popular"
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> setMostPopular(String planId) {
        Object[] plan;
        try {
            plan = (Object[]) em.createNativeQuery(
                    "SELECT is_active, is_current_version FROM plans WHERE id::text = :id"
            ).setParameter("id", planId).getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            throw new NotFoundException("Plano não encontrado");
        }

        boolean isActive  = (Boolean) plan[0];
        boolean isCurrent = (Boolean) plan[1];

        if (!isActive)  throw new BadRequestException("Planos inativos não podem ser definidos como Mais Popular");
        if (!isCurrent) throw new BadRequestException("Apenas a versão atual do plano pode ser marcada como Mais Popular");

        em.createNativeQuery("UPDATE plans SET is_most_popular = FALSE WHERE is_most_popular = TRUE")
          .executeUpdate();

        em.createNativeQuery(
                "UPDATE plans SET is_most_popular = TRUE, updated_at = NOW() WHERE id::text = :id"
        ).setParameter("id", planId).executeUpdate();

        return Map.of("id", planId, "is_most_popular", true);
    }

    // ----------------------------------------------------------------
    // Admin: ativar / desativar
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> togglePlanStatus(String planId) {
        int updated = em.createNativeQuery(
                "UPDATE plans SET is_active = NOT is_active, updated_at = NOW() WHERE id::text = :id"
        ).setParameter("id", planId).executeUpdate();

        if (updated == 0) throw new NotFoundException("Plano não encontrado");

        Object[] result = (Object[]) em.createNativeQuery(
                "SELECT is_active, is_most_popular FROM plans WHERE id::text = :id"
        ).setParameter("id", planId).getSingleResult();

        Boolean nowActive   = (Boolean) result[0];
        Boolean mostPopular = (Boolean) result[1];

        if (!nowActive && mostPopular) {
            em.createNativeQuery("UPDATE plans SET is_most_popular = FALSE WHERE id::text = :id")
              .setParameter("id", planId).executeUpdate();
        }

        return Map.of("id", planId, "is_active", nowActive);
    }

    // ----------------------------------------------------------------
    // Contagem de tenants ativos por código de plano
    // ----------------------------------------------------------------

    public long countActiveTenantsByPlan(String planCode) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(DISTINCT ts.tenant_id) FROM tenant_subscriptions ts " +
                "JOIN plans p ON p.id = ts.plan_id " +
                "WHERE p.code = :code AND ts.status IN ('trial', 'active')"
        ).setParameter("code", planCode).getSingleResult()).longValue();
    }

    // ----------------------------------------------------------------
    // Helper privado — inclui plan_type no índice 13
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Object[] fetchCurrentPlan(String planId) {
        try {
            return (Object[]) em.createNativeQuery(
                    "SELECT code, price_monthly, price_annual, max_users, max_ai_requests_month, " +
                    "features::text, version, name, description, sort_order, billing_type, " +
                    "discount_annual_percent, is_most_popular, plan_type " +
                    "FROM plans WHERE id::text = :id AND is_current_version = TRUE"
            ).setParameter("id", planId).getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            throw new NotFoundException("Plano não encontrado ou já não é a versão atual");
        }
    }

    // ----------------------------------------------------------------
    // DTO
    // ----------------------------------------------------------------

    public record PlanRequest(
        String name,
        String code,
        String description,
        BigDecimal priceMonthly,
        BigDecimal priceAnnual,
        Integer discountAnnualPercent,
        Integer maxUsers,
        Integer maxAiRequestsMonth,
        Map<String, Object> features,
        String billingType,
        Integer sortOrder,
        String planType
    ) {
        public String featuresJson() {
            if (features == null) return null;
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(features);
            } catch (Exception e) {
                throw new IllegalArgumentException("Features JSON inválido");
            }
        }
    }
}
