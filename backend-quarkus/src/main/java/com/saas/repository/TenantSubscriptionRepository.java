package com.saas.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TenantSubscriptionRepository {

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper objectMapper;

    public record SubscriptionResult(
            UUID id,
            String status,
            String planCode,
            Map<String, Object> planFeatures
    ) {}

    public Optional<SubscriptionResult> findActiveByTenant(UUID tenantId) {
        var result = em.createNativeQuery(
                "SELECT ts.id, ts.status, p.code, p.features " +
                "FROM tenant_subscriptions ts " +
                "JOIN plans p ON p.id = ts.plan_id " +
                "WHERE ts.tenant_id = :tenantId " +
                "AND ts.status NOT IN ('cancelled') " +
                "ORDER BY ts.created_at DESC LIMIT 1",
                Object[].class
        )
        .setParameter("tenantId", tenantId)
        .getResultList();

        if (result.isEmpty()) return Optional.empty();
        Object[] row = (Object[]) result.get(0);

        try {
            String featuresJson = (String) row[3];
            Map<String, Object> features = objectMapper.readValue(
                featuresJson, new TypeReference<>() {}
            );
            return Optional.of(new SubscriptionResult(
                    (UUID) row[0],
                    (String) row[1],
                    (String) row[2],
                    features
            ));
        } catch (Exception e) {
            throw new RuntimeException("Falha ao parsear features do plano", e);
        }
    }
}
