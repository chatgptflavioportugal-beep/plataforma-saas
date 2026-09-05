package com.saas.payment.dao;

import com.saas.payment.entity.PaymentWebhookEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentWebhookEventDAO implements PanacheRepositoryBase<PaymentWebhookEvent, UUID> {

    @Inject
    EntityManager em;

    /**
     * Insere o evento apenas se ainda não existir (gateway, external_event_id).
     * Native query com ON CONFLICT DO NOTHING é o mecanismo de idempotência:
     * um "find então persist" via ORM reintroduziria uma condição de corrida
     * (TOCTOU) entre duas entregas simultâneas do mesmo webhook — mesmo
     * princípio já usado em ProfileModuleSubscriptionDAO.upsertContractedModule
     * no subscription-service.
     *
     * @return true se o evento foi inserido agora (primeira vez); false se já existia (duplicado).
     */
    @Transactional
    public boolean insertIfNew(String gateway, String externalEventId, String eventType, String rawPayload) {
        List<?> result = em.createNativeQuery("""
                INSERT INTO payment_webhook_events (id, gateway, external_event_id, event_type, payload, processed, created_at)
                VALUES (gen_random_uuid(), :gateway, :externalEventId, :eventType, CAST(:payload AS jsonb), false, now())
                ON CONFLICT (gateway, external_event_id) DO NOTHING
                RETURNING id
                """)
                .setParameter("gateway", gateway)
                .setParameter("externalEventId", externalEventId)
                .setParameter("eventType", eventType)
                .setParameter("payload", rawPayload)
                .getResultList();
        return !result.isEmpty();
    }

    @Transactional
    public void markProcessed(String gateway, String externalEventId) {
        update("processed = true, processedAt = ?1 where gateway = ?2 and externalEventId = ?3",
                java.time.OffsetDateTime.now(), gateway, externalEventId);
    }
}
