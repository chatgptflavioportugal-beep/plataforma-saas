package com.saas.subscription.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Leitura pública (SELECT-only) do catálogo de planos, consumida por
 * PublicResource (tela de contratação do cliente). Todo o CRUD administrativo
 * de planos (criar, versionar, editar módulos/limites) vive em admin-service
 * — subscription-service só consome esses dados, nunca os altera.
 */
@ApplicationScoped
public class PlanService {

    @Inject
    EntityManager em;

    // ----------------------------------------------------------------
    // Expressões SQL reutilizáveis para cálculo de totais pelos módulos
    // total_monthly_price        = soma dos preços mensais dos módulos ativos
    // total_annual_monthly_price = soma dos preços anual/mês dos módulos ativos
    // total_annual_price         = total_annual_monthly_price * 12
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

    private static final String MODULES_JSON_EXPR =
        "COALESCE((SELECT json_agg(json_build_object(" +
        "  'module_name', pm.name, 'module_slug', pm.slug, 'module_icon_path', pm.icon_path" +
        ") ORDER BY pvm.sort_order) FROM plan_version_modules pvm " +
        " JOIN platform_modules pm ON pm.id = pvm.module_id" +
        " WHERE pvm.plan_id = p.id AND pvm.status = 'active'), '[]'::json)::text";

    // ----------------------------------------------------------------
    // Leitura pública: versões atuais ativas, com filtro opcional por tipo
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listActivePlans(String planType) {
        String typeFilter = (planType != null && !planType.isBlank())
                ? " AND plan_type = '" + planType.replace("'", "''") + "'"
                : "";

        List<Object[]> rows = em.createNativeQuery(
                "SELECT id::text, name, code, description, price_monthly, price_annual, " +
                "discount_annual_percent, max_users, max_ai_requests_month, " +
                "sort_order, version, billing_type, is_most_popular, plan_type, " +
                TOTAL_MONTHLY_EXPR + " AS total_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " AS total_annual_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " * 12 AS total_annual_price, " +
                MODULE_COUNT_EXPR + " AS module_count, " +
                MODULES_JSON_EXPR + " AS modules_json " +
                "FROM plans p " +
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
            m.put("sort_order", row[9]);
            m.put("version", row[10]);
            m.put("billing_type", row[11]);
            m.put("is_most_popular", row[12]);
            m.put("plan_type", row[13]);
            m.put("total_monthly_price", row[14]);
            m.put("total_annual_monthly_price", row[15]);
            m.put("total_annual_price", row[16]);
            m.put("module_count", row[17]);
            m.put("modules_json", row[18]);
            return m;
        }).collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Leitura pública: módulos com planos disponíveis (tela de contratação)
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listModuleBillingOptions() {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT pm.id::text, pm.name, pm.slug, pm.description, pm.icon_path, " +
            "COALESCE((SELECT json_agg(json_build_object(" +
            "  'id', pms.id::text, 'name', pms.name, 'description', pms.description, 'icon_path', pms.icon_path" +
            ") ORDER BY pms.sort_order, pms.name) FROM platform_module_services pms" +
            " WHERE pms.module_id = pm.id AND pms.is_active = TRUE), '[]'::json)::text AS services_json, " +
            "COALESCE((SELECT json_agg(json_build_object(" +
            "  'plan_id', p.id::text, 'plan_name', p.name, 'plan_slug', p.code," +
            "  'plan_version_id', pvm.id::text, 'plan_version', p.version," +
            "  'plan_sort_order', p.sort_order," +
            "  'monthly_price', pvm.monthly_price, 'annual_monthly_price', pvm.annual_monthly_price," +
            "  'annual_total_price', pvm.annual_monthly_price * 12," +
            "  'trial_available', trial_offer.id IS NOT NULL," +
            "  'trial_campaign_name', trial_offer.name," +
            "  'trial_days', trial_offer.days," +
            "  'limits', COALESCE((SELECT json_agg(json_build_object(" +
            "    'title', pvml.title, 'description', pvml.description," +
            "    'limit_value', pvml.limit_value, 'unit', pvml.unit, 'sort_order', pvml.sort_order" +
            "  ) ORDER BY pvml.sort_order) FROM plan_version_module_limits pvml" +
            "  WHERE pvml.plan_version_module_id = pvm.id), '[]'::json)" +
            ") ORDER BY p.sort_order, pvm.monthly_price) FROM plan_version_modules pvm" +
            " JOIN plans p ON p.id = pvm.plan_id" +
            // Campanha de Trial vigente deste plan_version_module — mesma regra de
            // seleção de TrialCampaignService.resolveCatalogOffer(); mantenha as duas
            // em sincronia se a lógica de elegibilidade mudar.
            " LEFT JOIN LATERAL (" +
            "   SELECT tc.id, tc.name, tc.days FROM trial_campaigns tc" +
            "   WHERE tc.plan_version_module_id = pvm.id AND tc.status = 'ACTIVE'" +
            "     AND tc.used_slots < tc.max_slots" +
            "     AND (tc.start_date IS NULL OR tc.start_date <= CURRENT_DATE)" +
            "     AND (tc.end_date IS NULL OR tc.end_date >= CURRENT_DATE)" +
            "   ORDER BY tc.priority DESC, tc.created_at ASC LIMIT 1" +
            " ) trial_offer ON TRUE" +
            " WHERE pvm.module_id = pm.id AND pvm.status = 'active'" +
            " AND p.is_active = TRUE AND p.is_current_version = TRUE), '[]'::json)::text AS available_plans_json " +
            "FROM platform_modules pm WHERE pm.is_active = TRUE ORDER BY pm.sort_order, pm.name"
        ).getResultList();

        return rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("module_id", row[0]);
            m.put("module_name", row[1]);
            m.put("module_slug", row[2]);
            m.put("module_description", row[3]);
            m.put("icon_path", row[4]);
            m.put("services_json", row[5]);
            m.put("available_plans_json", row[6]);
            return m;
        }).collect(Collectors.toList());
    }
}
