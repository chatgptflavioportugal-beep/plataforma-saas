package com.saas.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Map;

/**
 * Listagem/detalhe administrativo de tenants, usada por AdminResource.
 * Migrado do backend-quarkus (Fase Admin Service).
 */
@ApplicationScoped
public class TenantService {

    @Inject
    EntityManager em;

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
