package com.saas.profile.dao;

import com.saas.profile.to.IndividualTenantTO;
import com.saas.profile.to.TenantProfileTO;
import com.saas.platformdatabase.query.DatabaseQuery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistência de perfil de tenant: plano/assinatura e o backfill de tenant individual.
 * A criação/leitura do próprio registro `tenants` usa a entidade Panache {@link com.saas.profile.entity.Tenant}
 * diretamente na camada de negócio (Active Record já é a camada de persistência para esse caso simples).
 */
@ApplicationScoped
public class TenantDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    public Optional<TenantProfileTO> findTenantProfile(UUID tenantId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT t.id::text, t.name, t.slug, t.status, t.type, t.trial_ends_at::text,
                        ts.id::text as sub_id, ts.status as sub_status, ts.trial_end::text,
                        ts.current_period_start::text, ts.current_period_end::text,
                        ts.billing_type, ts.plan_version,
                        p.id::text as plan_id, p.name as plan_name, p.code as plan_code, p.plan_type,
                        p.price_monthly, p.price_annual, p.max_users, p.max_ai_requests_month,
                        (SELECT COALESCE(json_object_agg(pm.slug, true), '{}'::json)
                         FROM plan_version_modules pvm
                         JOIN platform_modules pm ON pm.id = pvm.module_id AND pm.is_active = true
                         WHERE pvm.plan_id = p.id AND pvm.status = 'active')::text AS features,
                        ut.role,
                        COALESCE((SELECT SUM(pvm.monthly_price) FROM plan_version_modules pvm WHERE pvm.plan_id = p.id AND pvm.status = 'active'), 0) AS total_monthly_price,
                        COALESCE((SELECT SUM(pvm.annual_monthly_price) FROM plan_version_modules pvm WHERE pvm.plan_id = p.id AND pvm.status = 'active'), 0) AS total_annual_monthly_price,
                        COALESCE((SELECT SUM(pvm.annual_monthly_price) FROM plan_version_modules pvm WHERE pvm.plan_id = p.id AND pvm.status = 'active'), 0) * 12 AS total_annual_price
                        FROM tenants t
                        LEFT JOIN tenant_subscriptions ts ON ts.tenant_id = t.id
                        LEFT JOIN plans p ON p.id = ts.plan_id
                        LEFT JOIN user_tenants ut ON ut.tenant_id = t.id
                        WHERE t.id = :tenantId
                        ORDER BY ts.created_at DESC LIMIT 1
                        """, TenantProfileTO.class)
                .setParameter("tenantId", tenantId)
                .getOptionalResult();
    }

    public Optional<UUID> findPlanIdByType(String type) {
        try {
            return Optional.of((UUID) em.createNativeQuery(
                    "SELECT id FROM plans WHERE plan_type = :planType AND is_active = TRUE " +
                    "AND is_current_version = TRUE ORDER BY sort_order LIMIT 1"
            ).setParameter("planType", type).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<UUID> findAnyActivePlanId() {
        try {
            return Optional.of((UUID) em.createNativeQuery(
                    "SELECT id FROM plans WHERE is_active = TRUE AND is_current_version = TRUE " +
                    "ORDER BY sort_order LIMIT 1"
            ).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public void insertOwnerLink(UUID userId, UUID tenantId) {
        em.createNativeQuery(
                "INSERT INTO user_tenants (user_id, tenant_id, role) VALUES (:userId, :tenantId, 'owner')"
        )
        .setParameter("userId", userId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    }

    public void insertSubscription(UUID tenantId, UUID planId) {
        em.createNativeQuery(
                "INSERT INTO tenant_subscriptions (tenant_id, plan_id, status) " +
                "VALUES (:tenantId, :planId, 'active')"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("planId", planId)
        .executeUpdate();
    }

    public void insertSubscriptionIfMissing(UUID tenantId, UUID planId) {
        em.createNativeQuery(
                "INSERT INTO tenant_subscriptions (tenant_id, plan_id, status) " +
                "VALUES (:tenantId, :planId, 'active') ON CONFLICT DO NOTHING"
        )
        .setParameter("tenantId", tenantId)
        .setParameter("planId", planId)
        .executeUpdate();
    }

    public Optional<IndividualTenantTO> findIndividualTenantByUser(UUID userId) {
        return databaseQuery
                .nativeQuery(em, """
                        SELECT t.id::text, t.name, t.slug FROM tenants t
                        JOIN user_tenants ut ON ut.tenant_id = t.id
                        WHERE ut.user_id = :userId AND t.type = 'individual'
                        """, IndividualTenantTO.class)
                .setParameter("userId", userId)
                .getOptionalResult();
    }
}
