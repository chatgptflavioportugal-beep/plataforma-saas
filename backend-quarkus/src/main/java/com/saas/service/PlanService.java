package com.saas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

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
    // Leitura pública: apenas versões atuais ativas
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listActivePlans() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT id::text, name, code, description, price_monthly, price_annual, " +
                "discount_annual_percent, max_users, max_ai_requests_month, features::text, " +
                "sort_order, version, billing_type " +
                "FROM plans " +
                "WHERE is_active = TRUE AND is_current_version = TRUE " +
                "ORDER BY sort_order"
        ).getResultList();

        return rows.stream().map(row -> {
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("id", row[0]);
            plan.put("name", row[1]);
            plan.put("code", row[2]);
            plan.put("description", row[3]);
            plan.put("price_monthly", row[4]);
            plan.put("price_annual", row[5]);
            plan.put("discount_annual_percent", row[6]);
            plan.put("max_users", row[7]);
            plan.put("max_ai_requests_month", row[8]);
            plan.put("features", row[9]);
            plan.put("sort_order", row[10]);
            plan.put("version", row[11]);
            plan.put("billing_type", row[12]);
            return plan;
        }).collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Admin: listagem com contagem de assinantes por versão
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listAllPlansAdmin() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT p.id::text, p.name, p.code, p.description, " +
                "p.price_monthly, p.price_annual, p.discount_annual_percent, " +
                "p.max_users, p.max_ai_requests_month, p.features::text, " +
                "p.is_active, p.sort_order, p.version, p.is_current_version, " +
                "p.parent_plan_id::text, p.billing_type, p.created_at::text, " +
                "COUNT(ts.id) AS subscriber_count " +
                "FROM plans p " +
                "LEFT JOIN tenant_subscriptions ts " +
                "  ON ts.plan_id = p.id AND ts.status IN ('trial', 'active', 'past_due') " +
                "GROUP BY p.id " +
                "ORDER BY p.code, p.version"
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
            m.put("subscriber_count", ((Number) row[17]).longValue());
            return m;
        }).collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Admin: histórico de versões de um plano pelo code
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPlanVersionHistory(String planCode) {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT p.id::text, p.name, p.code, p.description, " +
                "p.price_monthly, p.price_annual, p.discount_annual_percent, " +
                "p.max_users, p.max_ai_requests_month, p.features::text, " +
                "p.is_active, p.sort_order, p.version, p.is_current_version, " +
                "p.created_at::text, COUNT(ts.id) AS subscriber_count " +
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
            m.put("created_at", row[14]);
            m.put("subscriber_count", ((Number) row[15]).longValue());
            return m;
        }).collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Admin: criar novo plano (versão 1)
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> createPlan(PlanRequest req) {
        UUID id = UUID.randomUUID();
        String featuresJson = req.featuresJson();

        em.createNativeQuery(
                "INSERT INTO plans (id, code, name, description, price_monthly, price_annual, " +
                "discount_annual_percent, max_users, max_ai_requests_month, features, " +
                "is_active, sort_order, version, is_current_version, billing_type) " +
                "VALUES (:id, :code, :name, :description, :priceMonthly, :priceAnnual, " +
                ":discountAnnualPercent, :maxUsers, :maxAiRequestsMonth, :features::jsonb, " +
                "TRUE, :sortOrder, 1, TRUE, :billingType)"
        )
        .setParameter("id", id)
        .setParameter("code", req.code())
        .setParameter("name", req.name())
        .setParameter("description", req.description())
        .setParameter("priceMonthly", req.priceMonthly())
        .setParameter("priceAnnual", req.priceAnnual())
        .setParameter("discountAnnualPercent", req.discountAnnualPercent())
        .setParameter("maxUsers", req.maxUsers())
        .setParameter("maxAiRequestsMonth", req.maxAiRequestsMonth())
        .setParameter("features", featuresJson)
        .setParameter("sortOrder", req.sortOrder() != null ? req.sortOrder() : 99)
        .setParameter("billingType", req.billingType() != null ? req.billingType() : "both")
        .executeUpdate();

        return Map.of("id", id.toString(), "version", 1, "created", true);
    }

    // ----------------------------------------------------------------
    // Admin: atualizar plano — cria nova versão se price/rules mudaram
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> updatePlan(String planId, PlanRequest req) {
        Object[] current = (Object[]) em.createNativeQuery(
                "SELECT code, price_monthly, price_annual, max_users, max_ai_requests_month, " +
                "features::text, version, name, description, sort_order, billing_type, discount_annual_percent " +
                "FROM plans WHERE id::text = :id"
        ).setParameter("id", planId).getSingleResult();

        String code = (String) current[0];
        BigDecimal currentPriceMonthly = (BigDecimal) current[1];
        BigDecimal currentPriceAnnual  = (BigDecimal) current[2];
        int currentMaxUsers            = ((Number) current[3]).intValue();
        int currentMaxAi               = ((Number) current[4]).intValue();
        String currentFeatures         = (String) current[5];
        int currentVersion             = ((Number) current[6]).intValue();

        boolean priceChanged   = req.priceMonthly() != null && req.priceMonthly().compareTo(currentPriceMonthly) != 0;
        boolean annualChanged  = req.priceAnnual() != null && currentPriceAnnual != null
                                 && req.priceAnnual().compareTo(currentPriceAnnual) != 0;
        boolean limitsChanged  = (req.maxUsers() != null && req.maxUsers() != currentMaxUsers)
                                 || (req.maxAiRequestsMonth() != null && req.maxAiRequestsMonth() != currentMaxAi);
        boolean featuresChanged = req.featuresJson() != null && !req.featuresJson().equals(currentFeatures);

        boolean needsNewVersion = priceChanged || annualChanged || limitsChanged || featuresChanged;

        if (needsNewVersion) {
            // Marca versão anterior como não-atual
            em.createNativeQuery("UPDATE plans SET is_current_version = FALSE WHERE id::text = :id")
              .setParameter("id", planId)
              .executeUpdate();

            // Cria nova versão
            UUID newId = UUID.randomUUID();
            int newVersion = currentVersion + 1;

            em.createNativeQuery(
                    "INSERT INTO plans (id, code, name, description, price_monthly, price_annual, " +
                    "discount_annual_percent, max_users, max_ai_requests_month, features, " +
                    "is_active, sort_order, version, is_current_version, parent_plan_id, billing_type) " +
                    "VALUES (:id, :code, :name, :description, :priceMonthly, :priceAnnual, " +
                    ":discountAnnualPercent, :maxUsers, :maxAiRequestsMonth, :features::jsonb, " +
                    "TRUE, :sortOrder, :version, TRUE, :parentId, :billingType)"
            )
            .setParameter("id", newId)
            .setParameter("code", code)
            .setParameter("name", req.name() != null ? req.name() : current[7])
            .setParameter("description", req.description() != null ? req.description() : current[8])
            .setParameter("priceMonthly", req.priceMonthly() != null ? req.priceMonthly() : currentPriceMonthly)
            .setParameter("priceAnnual", req.priceAnnual() != null ? req.priceAnnual() : currentPriceAnnual)
            .setParameter("discountAnnualPercent", req.discountAnnualPercent() != null ? req.discountAnnualPercent() : current[11])
            .setParameter("maxUsers", req.maxUsers() != null ? req.maxUsers() : currentMaxUsers)
            .setParameter("maxAiRequestsMonth", req.maxAiRequestsMonth() != null ? req.maxAiRequestsMonth() : currentMaxAi)
            .setParameter("features", req.featuresJson() != null ? req.featuresJson() : currentFeatures)
            .setParameter("sortOrder", req.sortOrder() != null ? req.sortOrder() : current[9])
            .setParameter("version", newVersion)
            .setParameter("parentId", UUID.fromString(planId))
            .setParameter("billingType", req.billingType() != null ? req.billingType() : current[10])
            .executeUpdate();

            return Map.of("id", newId.toString(), "version", newVersion, "new_version_created", true);
        }

        // Apenas metadados (nome, descrição) — atualiza in-place
        em.createNativeQuery(
                "UPDATE plans SET " +
                "name = COALESCE(:name, name), " +
                "description = COALESCE(:description, description), " +
                "sort_order = COALESCE(:sortOrder, sort_order), " +
                "billing_type = COALESCE(:billingType, billing_type), " +
                "updated_at = NOW() " +
                "WHERE id::text = :id"
        )
        .setParameter("name", req.name())
        .setParameter("description", req.description())
        .setParameter("sortOrder", req.sortOrder())
        .setParameter("billingType", req.billingType())
        .setParameter("id", planId)
        .executeUpdate();

        return Map.of("id", planId, "version", currentVersion, "new_version_created", false);
    }

    // ----------------------------------------------------------------
    // Admin: ativar / desativar plano
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> togglePlanStatus(String planId) {
        int updated = em.createNativeQuery(
                "UPDATE plans SET is_active = NOT is_active, updated_at = NOW() " +
                "WHERE id::text = :id"
        ).setParameter("id", planId).executeUpdate();

        if (updated == 0) throw new jakarta.ws.rs.NotFoundException("Plano não encontrado");

        Boolean newStatus = (Boolean) em.createNativeQuery(
                "SELECT is_active FROM plans WHERE id::text = :id"
        ).setParameter("id", planId).getSingleResult();

        return Map.of("id", planId, "is_active", newStatus);
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
    // DTO interno para criação/atualização de plano
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
        Integer sortOrder
    ) {
        public String featuresJson() {
            if (features == null) return null;
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.writeValueAsString(features);
            } catch (Exception e) {
                throw new IllegalArgumentException("Features JSON inválido");
            }
        }
    }
}
