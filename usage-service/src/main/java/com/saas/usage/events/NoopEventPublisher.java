package com.saas.usage.events;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Implementação padrão enquanto o Kafka não é ativado: apenas loga o evento.
 * Trocar por um KafkaEventPublisher real é a única mudança necessária para ativar
 * a publicação de eventos.
 */
@ApplicationScoped
public class NoopEventPublisher implements EventPublisher {

    private static final Logger LOG = Logger.getLogger(NoopEventPublisher.class);

    @Override
    public void publish(UsageEvent event) {
        LOG.debugf("UsageEvent (não publicado — Kafka ainda não ativado): %s", event);
    }
}
