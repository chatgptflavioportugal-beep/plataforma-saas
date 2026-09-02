package com.saas.auth.dao;

import com.saas.auth.to.SubscriptionAccountTO;
import com.saas.platformdatabase.query.DatabaseQuery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class TenantSubscriptionDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    public record SubscriptionResult(
            UUID id,
            String status,
            String planCode,
            Set<String> moduleSlugSet
    ) {}

    public Optional<SubscriptionResult> findActiveByTenant(UUID tenantId) {
        // Verifica status da conta do tenant (trial/active/suspended/cancelled)
        Optional<SubscriptionAccountTO> account = databaseQuery
                .nativeQuery(em, """
                        SELECT ts.id, ts.status, p.code
                        FROM tenant_subscriptions ts
                        JOIN plans p ON p.id = ts.plan_id
                        WHERE ts.tenant_id = :tenantId
                        AND ts.status NOT IN ('cancelled')
                        ORDER BY ts.created_at DESC LIMIT 1
                        """, SubscriptionAccountTO.class)
                .setParameter("tenantId", tenantId)
                .getOptionalResult();

        if (account.isEmpty()) return Optional.empty();

        // Constrói moduleSlugSet a partir de assinaturas de módulo reais e ativas
        // (profile_module_subscriptions). Módulos com plano Free só entram aqui depois
        // de ativados via POST /api/v1/subscriptions/free (lazy activation) — a mera
        // disponibilidade de um plano Free não concede mais acesso automático.
        @SuppressWarnings("unchecked")
        List<String> slugRows = em.createNativeQuery(
                "SELECT DISTINCT pm.slug " +
                "FROM platform_modules pm " +
                "WHERE pm.is_active = TRUE " +
                "  AND EXISTS (" +
                "    SELECT 1 FROM profile_module_subscriptions pms " +
                "    WHERE pms.tenant_id = :tenantId " +
                "      AND pms.module_id = pm.id " +
                "      AND pms.status = 'ACTIVE'" +
                "      AND (pms.expires_at IS NULL OR pms.expires_at > NOW())" +
                "  )"
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        Set<String> moduleSlugSet = new HashSet<>(slugRows);

        return account.map(row -> new SubscriptionResult(row.id(), row.status(), row.planCode(), moduleSlugSet));
    }
}
