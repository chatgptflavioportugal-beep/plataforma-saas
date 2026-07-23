package com.saas.usage.events;

/**
 * Ponto de extensão para publicar UsageEvent em um message broker (Kafka). Não integrar
 * ainda — apenas a abstração, seguindo o mesmo padrão de infra desacoplada usado no
 * pdf-service (services/kafka).
 */
public interface EventPublisher {

    void publish(UsageEvent event);
}
