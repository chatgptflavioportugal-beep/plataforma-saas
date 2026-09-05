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

    /**
     * Grava o resultado final do processamento de um evento já inserido.
     * {@code processed} (booleano legado) continua sendo mantido em sincronia
     * com {@code status == PROCESSED} para não quebrar nada que ainda o leia.
     *
     * @param paymentId pagamento associado, ou null se o evento não correspondeu
     *                   a nenhum Payment conhecido (status IGNORED)
     * @param status PROCESSED, FAILED ou IGNORED
     * @param errorMessage mensagem de erro quando status = FAILED; null caso contrário
     */
    @Transactional
    public void markOutcome(String gateway, String externalEventId, UUID paymentId, String status, String errorMessage) {
        update("paymentId = ?1, status = ?2, errorMessage = ?3, processed = ?4, processedAt = ?5 " +
                        "where gateway = ?6 and externalEventId = ?7",
                paymentId, status, errorMessage, "PROCESSED".equals(status), java.time.OffsetDateTime.now(),
                gateway, externalEventId);
    }

    /**
     * Uma entrega duplicada do mesmo (gateway, external_event_id) não gera um
     * novo registro (idempotência) — mas a tentativa é contabilizada, para
     * que o admin veja quantas vezes o gateway reenviou o evento.
     */
    @Transactional
    public void incrementAttempts(String gateway, String externalEventId) {
        update("attempts = attempts + 1 where gateway = ?1 and externalEventId = ?2", gateway, externalEventId);
    }
}
