package com.saas.subscription.repository;

import com.saas.subscription.entity.Plan;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Leitura pública (SELECT-only) do catálogo de planos. Todo o CRUD
 * administrativo de planos vive em admin-service — subscription-service só
 * consome esses dados, nunca os altera.
 */
@ApplicationScoped
public class PlanRepository implements PanacheRepositoryBase<Plan, UUID> {

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

    public record PlanCatalogRow(
        UUID id, String name, String code, String description, BigDecimal priceMonthly, BigDecimal priceAnnual,
        BigDecimal discountAnnualPercent, int maxUsers, int maxAiRequestsMonth, int sortOrder, int version,
        String billingType, boolean isMostPopular, String planType, BigDecimal totalMonthlyPrice,
        BigDecimal totalAnnualMonthlyPrice, BigDecimal totalAnnualPrice, int moduleCount, String modulesJson
    ) {}

    /**
     * Versões correntes e ativas dos planos, com filtro opcional por tipo.
     * Mantida como Native Query: os totais/JSON de módulos são agregações
     * Postgres-específicas (json_agg/json_build_object) sem equivalente
     * direto em JPQL. O filtro por tipo usa bind parameter (:planType) — a
     * versão anterior concatenava o valor diretamente na string SQL.
     */
    @SuppressWarnings("unchecked")
    public List<PlanCatalogRow> listActivePlans(String planType) {
        return listActivePlans(planType, null, null);
    }

    /**
     * page/size opcionais (1-based) — quando ausentes, mantém o comportamento
     * histórico de retornar o catálogo inteiro (contrato preservado). Quando
     * informados, aplica LIMIT/OFFSET no próprio banco em vez de paginar em
     * memória após buscar tudo.
     */
    @SuppressWarnings("unchecked")
    public List<PlanCatalogRow> listActivePlans(String planType, Integer page, Integer size) {
        String typeFilter = (planType != null && !planType.isBlank()) ? " AND plan_type = :planType" : "";
        boolean paginate = page != null && size != null && page > 0 && size > 0;

        var query = em.createNativeQuery(
                "SELECT id, name, code, description, price_monthly, price_annual, " +
                "discount_annual_percent, max_users, max_ai_requests_month, " +
                "sort_order, version, billing_type, is_most_popular, plan_type, " +
                TOTAL_MONTHLY_EXPR + " AS total_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " AS total_annual_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " * 12 AS total_annual_price, " +
                MODULE_COUNT_EXPR + " AS module_count, " +
                MODULES_JSON_EXPR + " AS modules_json " +
                "FROM plans p " +
                "WHERE is_active = TRUE AND is_current_version = TRUE" + typeFilter +
                " ORDER BY sort_order" +
                (paginate ? " LIMIT :limit OFFSET :offset" : "")
        );
        if (!typeFilter.isEmpty()) query.setParameter("planType", planType);
        if (paginate) query.setParameter("limit", size).setParameter("offset", (page - 1) * size);

        List<Object[]> rows = query.getResultList();
        return rows.stream().map(row -> new PlanCatalogRow(
            (UUID) row[0], (String) row[1], (String) row[2], (String) row[3],
            (BigDecimal) row[4], (BigDecimal) row[5], (BigDecimal) row[6],
            ((Number) row[7]).intValue(), ((Number) row[8]).intValue(), ((Number) row[9]).intValue(),
            ((Number) row[10]).intValue(), (String) row[11], (Boolean) row[12], (String) row[13],
            (BigDecimal) row[14], (BigDecimal) row[15], (BigDecimal) row[16],
            ((Number) row[17]).intValue(), (String) row[18]
        )).toList();
    }

    public record ModuleBillingOptionRow(
        UUID moduleId, String moduleName, String moduleSlug, String moduleDescription,
        String iconPath, String servicesJson, String availablePlansJson
    ) {}

    /**
     * Módulos ativos com as opções de plano disponíveis para contratação.
     * Mantida como Native Query pelo mesmo motivo de listActivePlans, com o
     * agravante de uma LATERAL JOIN para resolver a campanha de Trial vigente
     * de cada plan_version_module — sem equivalente direto em JPQL.
     */
    public List<ModuleBillingOptionRow> listModuleBillingOptions() {
        return listModuleBillingOptions(null, null);
    }

    /** page/size opcionais (1-based) — mesmo raciocínio de listActivePlans(type, page, size). */
    @SuppressWarnings("unchecked")
    public List<ModuleBillingOptionRow> listModuleBillingOptions(Integer page, Integer size) {
        boolean paginate = page != null && size != null && page > 0 && size > 0;
        var query = em.createNativeQuery(
            "SELECT pm.id, pm.name, pm.slug, pm.description, pm.icon_path, " +
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
            // seleção de TrialCampaignRepository.findSelectableForPlanVersionModule;
            // mantenha as duas em sincronia se a lógica de elegibilidade mudar.
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
            "FROM platform_modules pm WHERE pm.is_active = TRUE ORDER BY pm.sort_order, pm.name" +
            (paginate ? " LIMIT :limit OFFSET :offset" : "")
        );
        if (paginate) query.setParameter("limit", size).setParameter("offset", (page - 1) * size);
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(row -> new ModuleBillingOptionRow(
            (UUID) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4],
            (String) row[5], (String) row[6]
        )).toList();
    }
}
