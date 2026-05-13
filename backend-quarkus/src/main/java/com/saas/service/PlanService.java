package com.saas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Map;

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

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listActivePlans() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT id::text, name, code, description, price_monthly, " +
                "max_users, max_ai_requests_month, features::text, sort_order " +
                "FROM plans WHERE is_active = TRUE ORDER BY sort_order"
        ).getResultList();

        return rows.stream().map(row -> {
            Map<String, Object> plan = new java.util.LinkedHashMap<>();
            plan.put("id", row[0]);
            plan.put("name", row[1]);
            plan.put("code", row[2]);
            plan.put("description", row[3]);
            plan.put("price_monthly", row[4]);
            plan.put("max_users", row[5]);
            plan.put("max_ai_requests_month", row[6]);
            plan.put("features", row[7]);
            plan.put("sort_order", row[8]);
            return plan;
        }).collect(java.util.stream.Collectors.toList());
    }

    public long countActiveTenantsByPlan(String planCode) {
        return (Long) em.createNativeQuery(
                "SELECT COUNT(DISTINCT ts.tenant_id) FROM tenant_subscriptions ts " +
                "JOIN plans p ON p.id = ts.plan_id " +
                "WHERE p.code = :code AND ts.status IN ('trial', 'active')"
        )
        .setParameter("code", planCode)
        .getSingleResult();
    }
}
