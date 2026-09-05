package com.saas.payment.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de controle de evento de webhook recebido de um gateway. A
 * unicidade (gateway, external_event_id) — ver migration 0054 — é o
 * mecanismo de idempotência: o mesmo evento reenviado pelo gateway nunca é
 * processado duas vezes.
 */
@Entity
@Table(name = "payment_webhook_events")
public class PaymentWebhookEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    public UUID id;

    @Column(nullable = false)
    public String gateway;

    @Column(name = "external_event_id", nullable = false)
    public String externalEventId;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    /**
     * Payload bruto do evento, sempre gravado/lido via SQL nativo com
     * {@code CAST(:payload AS jsonb)} (ver PaymentWebhookEventDAO) — nunca
     * persistido via Panache, então não precisa de conversor de tipo JSON aqui.
     */
    @Column(nullable = false)
    public String payload;

    @Column(nullable = false)
    public boolean processed = false;

    @Column(name = "processed_at")
    public OffsetDateTime processedAt;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();
}
