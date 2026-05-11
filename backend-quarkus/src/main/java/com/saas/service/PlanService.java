package com.saas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        return em.createNativeQuery(
                "SELECT id, name, code, description, price_monthly, " +
                "max_users, max_ai_requests_month, features, sort_order " +
                "FROM plans WHERE is_active = TRUE ORDER BY sort_order",
                "PlanDtoMapping"
        ).getResultList();
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
