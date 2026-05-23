package com.saas.service;

import com.saas.entity.Tenant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class TenantService {

    @Inject
    EntityManager em;

    @ConfigProperty(name = "trial.duration.days", defaultValue = "14")
    int trialDurationDays;

    // ----------------------------------------------------------------
    // Criar tenant de empresa (chamado pelo onboarding)
    // ----------------------------------------------------------------

    @Transactional
    public Tenant createTenant(String name, String slug, UUID ownerId) {
        return createTenant(name, slug, ownerId, "business");
    }

    @Transactional
    public Tenant createTenant(String name, String slug, UUID ownerId, String type) {
        if (Tenant.findBySlug(slug) != null) {
            throw new IllegalArgumentException("Slug já em uso: " + slug);
        }

        // Para empresa: procura plano business free. Para individual: plano individual.
        UUID freePlanId;
        try {
            freePlanId = (UUID) em.createNativeQuery(
                    "SELECT id FROM plans WHERE plan_type = :planType AND is_active = TRUE " +
                    "AND is_current_version = TRUE ORDER BY sort_order LIMIT 1"
            ).setParameter("planType", type).getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            // Fallback: qualquer plano ativo
            freePlanId = (UUID) em.createNativeQuery(
                    "SELECT id FROM plans WHERE is_active = TRUE AND is_current_version = TRUE " +
                    "ORDER BY sort_order LIMIT 1"
            ).getSingleResult();
        }

        Tenant tenant = new Tenant();
        tenant.name = name;
        tenant.slug = slug;
        tenant.status = "trial";
        tenant.planId = freePlanId;
        tenant.trialEndsAt = OffsetDateTime.now().plusDays(trialDurationDays);
        tenant.persist();

        em.createNativeQuery(
                "UPDATE tenants SET type = :type WHERE id = :id"
        )
        .setParameter("type", type)
        .setParameter("id", tenant.id)
        .executeUpdate();

        em.createNativeQuery(
                "INSERT INTO user_tenants (user_id, tenant_id, role) VALUES (:userId, :tenantId, 'owner')"
        )
        .setParameter("userId", ownerId)
        .setParameter("tenantId", tenant.id)
        .executeUpdate();

        em.createNativeQuery(
                "INSERT INTO tenant_subscriptions (tenant_id, plan_id, status, trial_start, trial_end) " +
                "VALUES (:tenantId, :planId, 'trial', NOW(), :trialEnd)"
        )
        .setParameter("tenantId", tenant.id)
        .setParameter("planId", freePlanId)
        .setParameter("trialEnd", tenant.trialEndsAt)
        .executeUpdate();

        return tenant;
    }

    // ----------------------------------------------------------------
    // Perfil do tenant: subscription + plano + papel do usuário
    // Se o tenant individual não tiver subscription ainda, cria automaticamente.
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> getTenantProfile(UUID tenantId) {
        var rows = (java.util.List<Object[]>) em.createNativeQuery(
                "SELECT t.id::text, t.name, t.slug, t.status, t.type, t.trial_ends_at::text, " +
                "ts.id::text as sub_id, ts.status as sub_status, ts.trial_end::text, " +
                "ts.current_period_start::text, ts.current_period_end::text, " +
                "ts.billing_type, ts.plan_version, " +
                "p.id::text as plan_id, p.name as plan_name, p.code as plan_code, p.plan_type, " +
                "p.price_monthly, p.price_annual, p.max_users, p.max_ai_requests_month, " +
                "(SELECT COALESCE(json_object_agg(pm.slug, true), '{}'::json) " +
                " FROM plan_version_modules pvm " +
                " JOIN platform_modules pm ON pm.id = pvm.module_id AND pm.is_active = true " +
                " WHERE pvm.plan_id = p.id AND pvm.status = 'active')::text AS features, " +
                "ut.role, " +
                "COALESCE((SELECT SUM(pvm.monthly_price) FROM plan_version_modules pvm WHERE pvm.plan_id = p.id AND pvm.status = 'active'), 0) AS total_monthly_price, " +
                "COALESCE((SELECT SUM(pvm.annual_monthly_price) FROM plan_version_modules pvm WHERE pvm.plan_id = p.id AND pvm.status = 'active'), 0) AS total_annual_monthly_price, " +
                "COALESCE((SELECT SUM(pvm.annual_monthly_price) FROM plan_version_modules pvm WHERE pvm.plan_id = p.id AND pvm.status = 'active'), 0) * 12 AS total_annual_price " +
                "FROM tenants t " +
                "LEFT JOIN tenant_subscriptions ts ON ts.tenant_id = t.id " +
                "LEFT JOIN plans p ON p.id = ts.plan_id " +
                "LEFT JOIN user_tenants ut ON ut.tenant_id = t.id " +
                "WHERE t.id = :tenantId " +
                "ORDER BY ts.created_at DESC LIMIT 1"
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        if (rows.isEmpty()) throw new NotFoundException("Tenant não encontrado");

        Object[] row = rows.get(0);
        String tenantType = (String) row[4];

        // Se não há subscription (tenant individual criado pelo trigger antes de ter planos),
        // cria automaticamente com o plano disponível de menor ordem.
        if (row[6] == null) {
            ensureSubscription(tenantId, tenantType);
            // Recarrega após criação
            return getTenantProfile(tenantId);
        }

        Map<String, Object> tenantMap = new java.util.LinkedHashMap<>();
        tenantMap.put("id", row[0]);
        tenantMap.put("name", row[1]);
        tenantMap.put("slug", row[2]);
        tenantMap.put("status", row[3]);
        tenantMap.put("type", row[4]);
        tenantMap.put("trial_ends_at", row[5]);

        Map<String, Object> subMap = new java.util.LinkedHashMap<>();
        subMap.put("id", row[6]);
        subMap.put("status", row[7]);
        subMap.put("trial_end", row[8]);
        subMap.put("current_period_start", row[9]);
        subMap.put("current_period_end", row[10]);
        subMap.put("billing_type", row[11] != null ? row[11] : "monthly");
        subMap.put("plan_version", row[12]);

        Map<String, Object> planMap = new java.util.LinkedHashMap<>();
        planMap.put("id", row[13]);
        planMap.put("name", row[14]);
        planMap.put("code", row[15]);
        planMap.put("plan_type", row[16]);
        planMap.put("price_monthly", row[17]);
        planMap.put("price_annual", row[18]);
        planMap.put("max_users", row[19]);
        planMap.put("max_ai_requests_month", row[20]);
        planMap.put("features", row[21]);
        planMap.put("total_monthly_price", row[23]);
        planMap.put("total_annual_monthly_price", row[24]);
        planMap.put("total_annual_price", row[25]);

        return Map.of(
                "tenant", tenantMap,
                "subscription", subMap,
                "plan", planMap,
                "role", row[22] != null ? row[22] : "owner"
        );
    }

    private void ensureSubscription(UUID tenantId, String tenantType) {
        UUID planId;
        try {
            planId = (UUID) em.createNativeQuery(
                    "SELECT id FROM plans WHERE plan_type = :planType AND is_active = TRUE " +
                    "AND is_current_version = TRUE ORDER BY sort_order LIMIT 1"
            ).setParameter("planType", tenantType).getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            try {
                planId = (UUID) em.createNativeQuery(
                        "SELECT id FROM plans WHERE is_active = TRUE AND is_current_version = TRUE " +
                        "ORDER BY sort_order LIMIT 1"
                ).getSingleResult();
            } catch (jakarta.persistence.NoResultException ex) {
                return; // Sem planos cadastrados ainda — não faz nada
            }
        }

        OffsetDateTime trialEnd = OffsetDateTime.now().plusDays(14);
        em.createNativeQuery(
                "INSERT INTO tenant_subscriptions (tenant_id, plan_id, status, trial_start, trial_end) " +
                "VALUES (:tenantId, :planId, 'trial', NOW(), :trialEnd) ON CONFLICT DO NOTHING"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("planId", planId)
        .setParameter("trialEnd", trialEnd)
        .executeUpdate();
    }

    // ----------------------------------------------------------------
    // Garante que o usuário tenha um tenant individual (backfill para usuários antigos)
    // Idempotente: se já existe, retorna o existente sem criar novo.
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> ensureIndividualTenant(UUID userId) {
        List<Object[]> existing = (java.util.List<Object[]>) em.createNativeQuery(
                "SELECT t.id::text, t.name, t.slug FROM tenants t " +
                "JOIN user_tenants ut ON ut.tenant_id = t.id " +
                "WHERE ut.user_id = :userId AND t.type = 'individual'"
        ).setParameter("userId", userId).getResultList();

        if (!existing.isEmpty()) {
            Object[] row = existing.get(0);
            return Map.of("id", row[0], "name", row[1], "slug", row[2],
                    "type", "individual", "already_exists", true);
        }

        String fullName;
        try {
            fullName = (String) em.createNativeQuery(
                    "SELECT full_name FROM user_profiles WHERE id = :userId"
            ).setParameter("userId", userId).getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            fullName = null;
        }

        String name = (fullName != null && !fullName.isBlank()) ? fullName.trim() : "Meu Plano";
        String slug = "individual-" + userId.toString().replace("-", "");

        Tenant tenant = createTenant(name, slug, userId, "individual");
        return Map.of("id", tenant.id.toString(), "name", tenant.name, "slug", tenant.slug,
                "type", "individual", "already_exists", false);
    }

    // ----------------------------------------------------------------
    // Listagem admin de tenants com filtros
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listAdminTenants(
            String search,
            String status,
            Boolean hasExtraMembers) {

        StringBuilder sql = new StringBuilder(
            "WITH latest_sub AS (" +
            "  SELECT DISTINCT ON (tenant_id) tenant_id, status AS sub_status, plan_id" +
            "  FROM tenant_subscriptions ORDER BY tenant_id, created_at DESC" +
            ") " +
            "SELECT" +
            "  t.id::text, t.name, t.slug, t.status, t.created_at::text, t.trial_ends_at::text," +
            "  p.name AS plan_name, p.code AS plan_code," +
            "  ls.sub_status AS subscription_status," +
            "  owner_up.full_name AS owner_name," +
            "  owner_au.email AS owner_email," +
            "  (SELECT COUNT(*)::int FROM user_tenants ut2" +
            "   WHERE ut2.tenant_id = t.id AND ut2.is_active = TRUE AND ut2.role != 'owner') AS member_count," +
            "  (SELECT COUNT(*)::int FROM invitations inv" +
            "   WHERE inv.tenant_id = t.id AND inv.status = 'pending') AS pending_invitations_count" +
            " FROM tenants t" +
            " LEFT JOIN latest_sub ls ON ls.tenant_id = t.id" +
            " LEFT JOIN plans p ON p.id = ls.plan_id" +
            " LEFT JOIN user_tenants owner_ut ON owner_ut.tenant_id = t.id AND owner_ut.role = 'owner' AND owner_ut.is_active = TRUE" +
            " LEFT JOIN user_profiles owner_up ON owner_up.id = owner_ut.user_id" +
            " LEFT JOIN auth.users owner_au ON owner_au.id = owner_ut.user_id" +
            " WHERE t.type = 'business'"
        );

        Map<String, Object> params = new java.util.LinkedHashMap<>();

        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(t.name) LIKE LOWER(:search)" +
                " OR LOWER(t.slug) LIKE LOWER(:search)" +
                " OR LOWER(owner_au.email) LIKE LOWER(:search)" +
                " OR LOWER(owner_up.full_name) LIKE LOWER(:search))");
            params.put("search", "%" + search.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND t.status = :status");
            params.put("status", status);
        }
        if (hasExtraMembers != null) {
            sql.append(hasExtraMembers
                ? " AND (SELECT COUNT(*) FROM user_tenants ut3 WHERE ut3.tenant_id = t.id AND ut3.role != 'owner' AND ut3.is_active = TRUE) > 0"
                : " AND (SELECT COUNT(*) FROM user_tenants ut3 WHERE ut3.tenant_id = t.id AND ut3.role != 'owner' AND ut3.is_active = TRUE) = 0");
        }

        sql.append(" ORDER BY t.created_at DESC");

        var query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);

        List<Object[]> rows = (List<Object[]>) query.getResultList();
        return rows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", row[0]);
            m.put("name", row[1]);
            m.put("slug", row[2]);
            m.put("status", row[3]);
            m.put("created_at", row[4]);
            m.put("trial_ends_at", row[5]);
            m.put("plan_name", row[6]);
            m.put("plan_code", row[7]);
            m.put("subscription_status", row[8]);
            m.put("owner_name", row[9]);
            m.put("owner_email", row[10]);
            m.put("member_count", row[11]);
            m.put("pending_invitations_count", row[12]);
            return m;
        }).collect(java.util.stream.Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Detalhe admin de um tenant
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAdminTenantDetail(String id) {
        List<Object[]> tenantRows = (List<Object[]>) em.createNativeQuery(
            "WITH latest_sub AS (" +
            "  SELECT DISTINCT ON (tenant_id) tenant_id, status AS sub_status, plan_id," +
            "    trial_start::text, trial_end::text," +
            "    current_period_start::text, current_period_end::text, billing_type" +
            "  FROM tenant_subscriptions ORDER BY tenant_id, created_at DESC" +
            ") " +
            "SELECT" +
            "  t.id::text, t.name, t.slug, t.status, t.created_at::text, t.trial_ends_at::text," +
            "  p.name AS plan_name, p.code AS plan_code," +
            "  ls.sub_status AS subscription_status," +
            "  ls.trial_start, ls.trial_end," +
            "  ls.current_period_start, ls.current_period_end, ls.billing_type," +
            "  owner_up.full_name AS owner_name," +
            "  owner_au.email AS owner_email," +
            "  owner_ut.created_at::text AS owner_joined_at" +
            " FROM tenants t" +
            " LEFT JOIN latest_sub ls ON ls.tenant_id = t.id" +
            " LEFT JOIN plans p ON p.id = ls.plan_id" +
            " LEFT JOIN user_tenants owner_ut ON owner_ut.tenant_id = t.id AND owner_ut.role = 'owner' AND owner_ut.is_active = TRUE" +
            " LEFT JOIN user_profiles owner_up ON owner_up.id = owner_ut.user_id" +
            " LEFT JOIN auth.users owner_au ON owner_au.id = owner_ut.user_id" +
            " WHERE t.id::text = :id AND t.type = 'business'"
        ).setParameter("id", id).getResultList();

        if (tenantRows.isEmpty()) return null;

        Object[] t = tenantRows.get(0);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", t[0]);
        result.put("name", t[1]);
        result.put("slug", t[2]);
        result.put("status", t[3]);
        result.put("created_at", t[4]);
        result.put("trial_ends_at", t[5]);
        result.put("plan_name", t[6]);
        result.put("plan_code", t[7]);
        result.put("subscription_status", t[8]);
        result.put("trial_start", t[9]);
        result.put("trial_end", t[10]);
        result.put("current_period_start", t[11]);
        result.put("current_period_end", t[12]);
        result.put("billing_type", t[13]);
        result.put("owner_name", t[14]);
        result.put("owner_email", t[15]);
        result.put("owner_joined_at", t[16]);

        List<Object[]> memberRows = (List<Object[]>) em.createNativeQuery(
            "SELECT up.full_name, au.email, ut.role, ut.is_active, ut.created_at::text" +
            " FROM user_tenants ut" +
            " JOIN user_profiles up ON up.id = ut.user_id" +
            " JOIN auth.users au ON au.id = ut.user_id" +
            " WHERE ut.tenant_id::text = :id" +
            " ORDER BY (ut.role = 'owner') DESC, ut.created_at ASC"
        ).setParameter("id", id).getResultList();

        result.put("members", memberRows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("full_name", row[0]);
            m.put("email", row[1]);
            m.put("role", row[2]);
            m.put("is_active", row[3]);
            m.put("joined_at", row[4]);
            return m;
        }).toList());

        List<Object[]> invRows = (List<Object[]>) em.createNativeQuery(
            "SELECT inv.email, inv.role, inv.created_at::text, up.full_name AS invited_by_name" +
            " FROM invitations inv" +
            " LEFT JOIN user_profiles up ON up.id = inv.invited_by" +
            " WHERE inv.tenant_id::text = :id AND inv.status = 'pending'" +
            " ORDER BY inv.created_at DESC"
        ).setParameter("id", id).getResultList();

        result.put("pending_invitations", invRows.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("email", row[0]);
            m.put("role", row[1]);
            m.put("created_at", row[2]);
            m.put("invited_by_name", row[3]);
            return m;
        }).toList());

        return result;
    }
}
