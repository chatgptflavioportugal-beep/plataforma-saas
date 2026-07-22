package com.saas.usage.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Evento de domínio emitido quando um contador de uso é incrementado ou um item de
 * auditoria de uso é registrado. Estrutura pronta para publicação via Kafka quando a
 * infraestrutura de mensageria for ativada (ver EventPublisher).
 */
public record UsageEvent(
        UUID tenantId,
        UUID userId,
        String moduleSlug,
        String metricCode,
        String action,
        Map<String, Object> metadata,
        Instant occurredAt
) {
}
