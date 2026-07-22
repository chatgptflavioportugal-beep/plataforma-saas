package com.saas.profile.service;

import com.saas.profile.entity.Tenant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Perfil/criação de tenant (empresa ou individual).
 *
 * A listagem/detalhe administrativo de tenants (usada por AdminResource em
 * backend-quarkus) permanece lá até a Fase 6 (admin-service) — este serviço cobre
 * apenas o que o próprio cliente vê/aciona sobre seu perfil.
 */
@ApplicationScoped
public class TenantService {

    @Inject
    EntityManager em;

    // ----------------------------------------------------------------
    // Criar tenant de empresa (chamado pelo onboarding)
    // ----------------------------------------------------------------

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
        tenant.status = "active";
        tenant.planId = freePlanId;
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
                "INSERT INTO tenant_subscriptions (tenant_id, plan_id, status) " +
                "VALUES (:tenantId, :planId, 'active')"
        )
        .setParameter("tenantId", tenant.id)
        .setParameter("planId", freePlanId)
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

        em.createNativeQuery(
                "INSERT INTO tenant_subscriptions (tenant_id, plan_id, status) " +
                "VALUES (:tenantId, :planId, 'active') ON CONFLICT DO NOTHING"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("planId", planId)
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
}
