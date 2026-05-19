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
                "discount_annual_percent, max_users, max_ai_requests_month, " +
                "sort_order, version, billing_type, is_most_popular, plan_type, " +
                TOTAL_MONTHLY_EXPR + " AS total_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " AS total_annual_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " * 12 AS total_annual_price, " +
                MODULE_COUNT_EXPR + " AS module_count " +
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
                "p.max_users, p.max_ai_requests_month, " +
                "p.is_active, p.sort_order, p.version, p.is_current_version, " +
                "p.parent_plan_id::text, p.billing_type, p.created_at::text, " +
                "p.is_most_popular, p.plan_type, " +
                "COUNT(ts.id) AS subscriber_count, " +
                TOTAL_MONTHLY_EXPR + " AS total_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " AS total_annual_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " * 12 AS total_annual_price, " +
                MODULE_COUNT_EXPR + " AS module_count " +
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
            m.put("is_active", row[9]);
            m.put("sort_order", row[10]);
            m.put("version", row[11]);
            m.put("is_current_version", row[12]);
            m.put("parent_plan_id", row[13]);
            m.put("billing_type", row[14]);
            m.put("created_at", row[15]);
            m.put("is_most_popular", row[16]);
            m.put("plan_type", row[17]);
            m.put("subscriber_count", ((Number) row[18]).longValue());
            m.put("total_monthly_price", row[19]);
            m.put("total_annual_monthly_price", row[20]);
            m.put("total_annual_price", row[21]);
            m.put("module_count", ((Number) row[22]).intValue());
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
                "p.max_users, p.max_ai_requests_month, " +
                "p.is_active, p.sort_order, p.version, p.is_current_version, " +
                "p.is_most_popular, p.created_at::text, p.plan_type, " +
                "COUNT(ts.id) AS subscriber_count, " +
                TOTAL_MONTHLY_EXPR + " AS total_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " AS total_annual_monthly_price, " +
                TOTAL_ANNUAL_MONTHLY_EXPR + " * 12 AS total_annual_price, " +
                MODULE_COUNT_EXPR + " AS module_count " +
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
            m.put("is_active", row[9]);
            m.put("sort_order", row[10]);
            m.put("version", row[11]);
            m.put("is_current_version", row[12]);
            m.put("is_most_popular", row[13]);
            m.put("created_at", row[14]);
            m.put("plan_type", row[15]);
            m.put("subscriber_count", ((Number) row[16]).longValue());
            m.put("total_monthly_price", row[17]);
            m.put("total_annual_monthly_price", row[18]);
            m.put("total_annual_price", row[19]);
            m.put("module_count", ((Number) row[20]).intValue());
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

        return Map.of("id", id.toString(), "version", 1, "created", true);
    }

    // ----------------------------------------------------------------
    // Admin: gerar nova versão (preserva plan_type e copia módulos)
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> createNewVersion(String currentPlanId, PlanRequest req) {
        Object[] current = fetchCurrentPlan(currentPlanId);

        String code          = (String)     current[0];
        int oldMaxUsers      = ((Number)    current[1]).intValue();
        int oldMaxAi         = ((Number)    current[2]).intValue();
        int oldVersion       = ((Number)    current[3]).intValue();
        String oldName       = (String)     current[4];
        String oldDesc       = (String)     current[5];
        int oldSortOrder     = ((Number)    current[6]).intValue();
        String oldBilling    = (String)     current[7];
        int oldDiscount      = ((Number)    current[8]).intValue();
        boolean wasMostPop   = (Boolean)    current[9];
        String oldPlanType   = (String)     current[10];

        em.createNativeQuery(
                "UPDATE plans SET is_current_version = FALSE, is_most_popular = FALSE, updated_at = NOW() " +
                "WHERE id::text = :id"
        ).setParameter("id", currentPlanId).executeUpdate();

        UUID newId = UUID.randomUUID();
        int newVersion = oldVersion + 1;

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
        .setParameter("name",              req.name() != null ? req.name() : oldName)
        .setParameter("description",       req.description() != null ? req.description() : oldDesc)
        .setParameter("discountAnnualPercent", req.discountAnnualPercent() != null ? req.discountAnnualPercent() : oldDiscount)
        .setParameter("maxUsers",          req.maxUsers() != null ? req.maxUsers() : oldMaxUsers)
        .setParameter("maxAiRequestsMonth",req.maxAiRequestsMonth() != null ? req.maxAiRequestsMonth() : oldMaxAi)
        .setParameter("sortOrder",         req.sortOrder() != null ? req.sortOrder() : oldSortOrder)
        .setParameter("version",           newVersion)
        .setParameter("parentId",          UUID.fromString(currentPlanId))
        .setParameter("billingType",       req.billingType() != null ? req.billingType() : oldBilling)
        .setParameter("isMostPopular",     wasMostPop)
        .setParameter("planType",          req.planType() != null ? req.planType() : oldPlanType)
        .executeUpdate();

        copyModulesToNewVersion(currentPlanId, newId);

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
    // Módulos de versão do plano — listagem (com limitações)
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listPlanVersionModules(String planId) {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT pvm.id::text, pvm.plan_id::text, pvm.module_id::text, " +
                "pm.name AS module_name, pm.slug AS module_slug, pm.icon_path AS module_icon_path, " +
                "pvm.monthly_price, pvm.annual_monthly_price, pvm.status, pvm.sort_order, " +
                "pvm.created_at::text, pvm.updated_at::text, " +
                "COALESCE((SELECT json_agg(json_build_object(" +
                "  'id', pvml.id::text, 'title', pvml.title, 'description', pvml.description, " +
                "  'limit_key', pvml.limit_key, 'limit_value', pvml.limit_value, " +
                "  'unit', pvml.unit, 'sort_order', pvml.sort_order" +
                ") ORDER BY pvml.sort_order) FROM plan_version_module_limits pvml " +
                "WHERE pvml.plan_version_module_id = pvm.id), '[]'::json)::text AS limits_json " +
                "FROM plan_version_modules pvm " +
                "JOIN platform_modules pm ON pm.id = pvm.module_id " +
                "WHERE pvm.plan_id::text = :planId " +
                "ORDER BY pvm.sort_order, pm.name"
        ).setParameter("planId", planId).getResultList();

        return rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("plan_id", row[1]);
            m.put("module_id", row[2]);
            m.put("module_name", row[3]);
            m.put("module_slug", row[4]);
            m.put("module_icon_path", row[5]);
            m.put("monthly_price", row[6]);
            m.put("annual_monthly_price", row[7]);
            m.put("status", row[8]);
            m.put("sort_order", row[9]);
            m.put("created_at", row[10]);
            m.put("updated_at", row[11]);
            m.put("limits_json", row[12]);
            return m;
        }).collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Módulos de versão do plano — adicionar
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> addPlanVersionModule(String planId, PlanVersionModuleRequest req) {
        long planExists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM plans WHERE id::text = :id"
        ).setParameter("id", planId).getSingleResult()).longValue();
        if (planExists == 0) throw new NotFoundException("Plano não encontrado");

        checkNoSubscribers(planId);

        if (req.moduleId() == null || req.moduleId().isBlank())
            throw new BadRequestException("moduleId é obrigatório");

        long dup = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM plan_version_modules WHERE plan_id::text = :planId AND module_id::text = :moduleId"
        ).setParameter("planId", planId).setParameter("moduleId", req.moduleId()).getSingleResult()).longValue();
        if (dup > 0) throw new BadRequestException("Este módulo já está adicionado a esta versão do plano");

        UUID id = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO plan_version_modules (id, plan_id, module_id, monthly_price, annual_monthly_price, status, sort_order) " +
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

        return Map.of("id", id.toString(), "created", true);
    }

    // ----------------------------------------------------------------
    // Módulos de versão do plano — atualizar
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> updatePlanVersionModule(String pvmId, PlanVersionModuleRequest req) {
        String planId = getPlanIdFromPvm(pvmId);
        checkNoSubscribers(planId);

        int updated = em.createNativeQuery(
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

        if (updated == 0) throw new NotFoundException("Módulo não encontrado neste plano");
        return Map.of("id", pvmId, "updated", true);
    }

    // ----------------------------------------------------------------
    // Módulos de versão do plano — remover
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> removePlanVersionModule(String pvmId) {
        String planId = getPlanIdFromPvm(pvmId);
        checkNoSubscribers(planId);

        int deleted = em.createNativeQuery(
                "DELETE FROM plan_version_modules WHERE id::text = :id"
        ).setParameter("id", pvmId).executeUpdate();
        if (deleted == 0) throw new NotFoundException("Módulo não encontrado neste plano");
        return Map.of("id", pvmId, "deleted", true);
    }

    // ----------------------------------------------------------------
    // Limitações do módulo — adicionar
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> addPlanVersionModuleLimit(String pvmId, PlanVersionModuleLimitRequest req) {
        if (req.title() == null || req.title().isBlank())
            throw new BadRequestException("title é obrigatório");

        long pvmExists = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM plan_version_modules WHERE id::text = :id"
        ).setParameter("id", pvmId).getSingleResult()).longValue();
        if (pvmExists == 0) throw new NotFoundException("Módulo do plano não encontrado");

        UUID id = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO plan_version_module_limits " +
                "(id, plan_version_module_id, title, description, limit_key, limit_value, unit, sort_order) " +
                "VALUES (:id, CAST(:pvmId AS uuid), :title, :description, :limitKey, :limitValue, :unit, :sortOrder)"
        )
        .setParameter("id", id)
        .setParameter("pvmId", pvmId)
        .setParameter("title", req.title())
        .setParameter("description", req.description())
        .setParameter("limitKey", req.limitKey())
        .setParameter("limitValue", req.limitValue())
        .setParameter("unit", req.unit())
        .setParameter("sortOrder", req.sortOrder() != null ? req.sortOrder() : 99)
        .executeUpdate();

        return Map.of("id", id.toString(), "created", true);
    }

    // ----------------------------------------------------------------
    // Limitações do módulo — atualizar
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> updatePlanVersionModuleLimit(String limitId, PlanVersionModuleLimitRequest req) {
        if (req.title() == null || req.title().isBlank())
            throw new BadRequestException("title é obrigatório");

        int updated = em.createNativeQuery(
                "UPDATE plan_version_module_limits SET " +
                "title = :title, description = :description, limit_key = :limitKey, " +
                "limit_value = :limitValue, unit = :unit, sort_order = :sortOrder, updated_at = NOW() " +
                "WHERE id::text = :id"
        )
        .setParameter("title", req.title())
        .setParameter("description", req.description())
        .setParameter("limitKey", req.limitKey())
        .setParameter("limitValue", req.limitValue())
        .setParameter("unit", req.unit())
        .setParameter("sortOrder", req.sortOrder() != null ? req.sortOrder() : 99)
        .setParameter("id", limitId)
        .executeUpdate();

        if (updated == 0) throw new NotFoundException("Limitação não encontrada");
        return Map.of("id", limitId, "updated", true);
    }

    // ----------------------------------------------------------------
    // Limitações do módulo — remover
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> removePlanVersionModuleLimit(String limitId) {
        int deleted = em.createNativeQuery(
                "DELETE FROM plan_version_module_limits WHERE id::text = :id"
        ).setParameter("id", limitId).executeUpdate();
        if (deleted == 0) throw new NotFoundException("Limitação não encontrada");
        return Map.of("id", limitId, "deleted", true);
    }

    // ----------------------------------------------------------------
    // Helper privado — copia módulos e limitações para nova versão
    // ----------------------------------------------------------------

    private void copyModulesToNewVersion(String oldPlanId, UUID newPlanId) {
        em.createNativeQuery(
                "INSERT INTO plan_version_modules (plan_id, module_id, monthly_price, annual_monthly_price, status, sort_order) " +
                "SELECT CAST(:newPlanId AS uuid), module_id, monthly_price, annual_monthly_price, status, sort_order " +
                "FROM plan_version_modules WHERE plan_id::text = :oldPlanId"
        ).setParameter("newPlanId", newPlanId.toString()).setParameter("oldPlanId", oldPlanId).executeUpdate();

        em.createNativeQuery(
                "INSERT INTO plan_version_module_limits " +
                "(plan_version_module_id, title, description, limit_key, limit_value, unit, sort_order) " +
                "SELECT new_pvm.id, old_l.title, old_l.description, old_l.limit_key, old_l.limit_value, old_l.unit, old_l.sort_order " +
                "FROM plan_version_module_limits old_l " +
                "JOIN plan_version_modules old_pvm ON old_pvm.id = old_l.plan_version_module_id " +
                "JOIN plan_version_modules new_pvm " +
                "  ON new_pvm.plan_id = CAST(:newPlanId AS uuid) AND new_pvm.module_id = old_pvm.module_id " +
                "WHERE old_pvm.plan_id::text = :oldPlanId"
        ).setParameter("newPlanId", newPlanId.toString()).setParameter("oldPlanId", oldPlanId).executeUpdate();
    }

    // ----------------------------------------------------------------
    // Helper privado — obtém plan_id a partir do pvmId (lança 404 se não existe)
    // ----------------------------------------------------------------

    private String getPlanIdFromPvm(String pvmId) {
        try {
            return (String) em.createNativeQuery(
                    "SELECT plan_id::text FROM plan_version_modules WHERE id::text = :id"
            ).setParameter("id", pvmId).getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            throw new NotFoundException("Módulo não encontrado neste plano");
        }
    }

    // ----------------------------------------------------------------
    // Helper privado — bloqueia alteração se a versão do plano já tem assinantes
    // ----------------------------------------------------------------

    private void checkNoSubscribers(String planId) {
        long subscribers = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM tenant_subscriptions " +
                "WHERE plan_id::text = :planId AND status IN ('trial', 'active', 'past_due')"
        ).setParameter("planId", planId).getSingleResult()).longValue();
        if (subscribers > 0)
            throw new BadRequestException(
                "Esta versão possui " + subscribers + " assinante(s). " +
                "Crie uma nova versão para alterar os módulos."
            );
    }

    // ----------------------------------------------------------------
    // Helper privado — busca dados da versão atual (sem price_monthly/annual)
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Object[] fetchCurrentPlan(String planId) {
        try {
            return (Object[]) em.createNativeQuery(
                    "SELECT code, max_users, max_ai_requests_month, " +
                    "version, name, description, sort_order, billing_type, " +
                    "discount_annual_percent, is_most_popular, plan_type " +
                    "FROM plans WHERE id::text = :id AND is_current_version = TRUE"
            ).setParameter("id", planId).getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            throw new NotFoundException("Plano não encontrado ou já não é a versão atual");
        }
    }

    // ----------------------------------------------------------------
    // DTOs
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
        String billingType,
        Integer sortOrder,
        String planType
    ) {}

    public record PlanVersionModuleRequest(
        String moduleId,
        BigDecimal monthlyPrice,
        BigDecimal annualMonthlyPrice,
        String status,
        Integer sortOrder
    ) {}

    public record PlanVersionModuleLimitRequest(
        String title,
        String description,
        String limitKey,
        String limitValue,
        String unit,
        Integer sortOrder
    ) {}

    public record PlanModuleWithLimitsRequest(
        String moduleId,
        BigDecimal monthlyPrice,
        BigDecimal annualMonthlyPrice,
        String status,
        Integer sortOrder,
        List<PlanVersionModuleLimitRequest> limits
    ) {}

    // ----------------------------------------------------------------
    // Admin: gerar nova versão com módulos completos (edição unificada)
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> createNewVersionWithModules(
            String currentPlanId,
            PlanRequest req,
            List<PlanModuleWithLimitsRequest> modules) {

        Object[] current = fetchCurrentPlan(currentPlanId);

        String code        = (String)  current[0];
        int oldMaxUsers    = ((Number) current[1]).intValue();
        int oldMaxAi       = ((Number) current[2]).intValue();
        int oldVersion     = ((Number) current[3]).intValue();
        String oldName     = (String)  current[4];
        String oldDesc     = (String)  current[5];
        int oldSortOrder   = ((Number) current[6]).intValue();
        String oldBilling  = (String)  current[7];
        int oldDiscount    = ((Number) current[8]).intValue();
        boolean wasMostPop = (Boolean) current[9];
        String oldPlanType = (String)  current[10];

        em.createNativeQuery(
                "UPDATE plans SET is_current_version = FALSE, is_most_popular = FALSE, updated_at = NOW() " +
                "WHERE id::text = :id"
        ).setParameter("id", currentPlanId).executeUpdate();

        UUID newId = UUID.randomUUID();
        int newVersion = oldVersion + 1;

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
        .setParameter("name",               req.name()                 != null ? req.name()                 : oldName)
        .setParameter("description",        req.description()           != null ? req.description()           : oldDesc)
        .setParameter("discountAnnualPercent", req.discountAnnualPercent() != null ? req.discountAnnualPercent() : oldDiscount)
        .setParameter("maxUsers",           req.maxUsers()             != null ? req.maxUsers()             : oldMaxUsers)
        .setParameter("maxAiRequestsMonth", req.maxAiRequestsMonth()   != null ? req.maxAiRequestsMonth()   : oldMaxAi)
        .setParameter("sortOrder",          req.sortOrder()            != null ? req.sortOrder()            : oldSortOrder)
        .setParameter("version",            newVersion)
        .setParameter("parentId",           UUID.fromString(currentPlanId))
        .setParameter("billingType",        req.billingType()          != null ? req.billingType()          : oldBilling)
        .setParameter("isMostPopular",      wasMostPop)
        .setParameter("planType",           req.planType()             != null ? req.planType()             : oldPlanType)
        .executeUpdate();

        if (modules != null) {
            for (PlanModuleWithLimitsRequest mod : modules) {
                UUID pvmId = UUID.randomUUID();
                em.createNativeQuery(
                        "INSERT INTO plan_version_modules " +
                        "(id, plan_id, module_id, monthly_price, annual_monthly_price, status, sort_order) " +
                        "VALUES (:id, CAST(:planId AS uuid), CAST(:moduleId AS uuid), " +
                        ":monthlyPrice, :annualMonthlyPrice, :status, :sortOrder)"
                )
                .setParameter("id", pvmId)
                .setParameter("planId", newId.toString())
                .setParameter("moduleId", mod.moduleId())
                .setParameter("monthlyPrice", mod.monthlyPrice() != null ? mod.monthlyPrice() : BigDecimal.ZERO)
                .setParameter("annualMonthlyPrice", mod.annualMonthlyPrice() != null ? mod.annualMonthlyPrice() : BigDecimal.ZERO)
                .setParameter("status", mod.status() != null ? mod.status() : "active")
                .setParameter("sortOrder", mod.sortOrder() != null ? mod.sortOrder() : 99)
                .executeUpdate();

                if (mod.limits() != null) {
                    for (PlanVersionModuleLimitRequest lim : mod.limits()) {
                        em.createNativeQuery(
                                "INSERT INTO plan_version_module_limits " +
                                "(id, plan_version_module_id, title, description, limit_key, limit_value, unit, sort_order) " +
                                "VALUES (:id, :pvmId, :title, :description, :limitKey, :limitValue, :unit, :sortOrder)"
                        )
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("pvmId", pvmId)
                        .setParameter("title", lim.title())
                        .setParameter("description", lim.description())
                        .setParameter("limitKey", lim.limitKey())
                        .setParameter("limitValue", lim.limitValue())
                        .setParameter("unit", lim.unit())
                        .setParameter("sortOrder", lim.sortOrder() != null ? lim.sortOrder() : 99)
                        .executeUpdate();
                    }
                }
            }
        } else {
            copyModulesToNewVersion(currentPlanId, newId);
        }

        return Map.of("id", newId.toString(), "version", newVersion, "new_version_created", true);
    }
}
