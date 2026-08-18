package com.saas.admin.dao;

import com.saas.admin.dto.TenantDetailDTO;
import com.saas.admin.dto.TenantInvitationDTO;
import com.saas.admin.dto.TenantMemberDTO;
import com.saas.admin.dto.TenantSummaryDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class TenantDAO {

    @Inject
    EntityManager em;

    @SuppressWarnings("unchecked")
    public List<TenantSummaryDTO> findAdminTenants(String search, String status, Boolean hasExtraMembers) {
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

        Map<String, Object> params = new LinkedHashMap<>();
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

        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(row -> new TenantSummaryDTO(
            (String) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4],
            (String) row[5], (String) row[6], (String) row[7], (String) row[8], (String) row[9],
            (String) row[10], (Integer) row[11], (Integer) row[12]
        )).toList();
    }

    @SuppressWarnings("unchecked")
    public Optional<TenantDetailDTO> findAdminTenantDetail(String id) {
        List<Object[]> tenantRows = em.createNativeQuery(
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

        if (tenantRows.isEmpty()) return Optional.empty();
        Object[] t = tenantRows.get(0);

        List<Object[]> memberRows = em.createNativeQuery(
            "SELECT up.full_name, au.email, ut.role, ut.is_active, ut.created_at::text" +
            " FROM user_tenants ut" +
            " JOIN user_profiles up ON up.id = ut.user_id" +
            " JOIN auth.users au ON au.id = ut.user_id" +
            " WHERE ut.tenant_id::text = :id" +
            " ORDER BY (ut.role = 'owner') DESC, ut.created_at ASC"
        ).setParameter("id", id).getResultList();

        List<TenantMemberDTO> members = memberRows.stream().map(row -> new TenantMemberDTO(
            (String) row[0], (String) row[1], (String) row[2], (Boolean) row[3], (String) row[4]
        )).toList();

        List<Object[]> invRows = em.createNativeQuery(
            "SELECT inv.email, inv.role, inv.created_at::text, up.full_name AS invited_by_name" +
            " FROM invitations inv" +
            " LEFT JOIN user_profiles up ON up.id = inv.invited_by" +
            " WHERE inv.tenant_id::text = :id AND inv.status = 'pending'" +
            " ORDER BY inv.created_at DESC"
        ).setParameter("id", id).getResultList();

        List<TenantInvitationDTO> invitations = invRows.stream().map(row -> new TenantInvitationDTO(
            (String) row[0], (String) row[1], (String) row[2], (String) row[3]
        )).toList();

        return Optional.of(new TenantDetailDTO(
            (String) t[0], (String) t[1], (String) t[2], (String) t[3], (String) t[4], (String) t[5],
            (String) t[6], (String) t[7], (String) t[8], (String) t[9], (String) t[10], (String) t[11],
            (String) t[12], (String) t[13], (String) t[14], (String) t[15], (String) t[16],
            members, invitations));
    }

    public int updateTenantStatus(String id, String status) {
        return em.createNativeQuery(
            "UPDATE tenants SET status = :status, updated_at = NOW() WHERE id::text = :id"
        ).setParameter("status", status).setParameter("id", id).executeUpdate();
    }
}
