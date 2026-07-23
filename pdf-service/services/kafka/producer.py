"""
Abstração de producer Kafka — interface pronta, sem broker real ainda.

Quando o Kafka for ativado, uma implementação concreta (ex.: usando
`aiokafka`) deve satisfazer este Protocol e ser injetada onde
`get_event_producer()` é chamado hoje.
"""

import logging
from typing import Protocol

from services.kafka.events import DomainEvent

logger = logging.getLogger(__name__)


class EventProducer(Protocol):
    async def publish(self, topic: str, event: DomainEvent) -> None: ...


class LoggingEventProducer:
    """Implementação padrão enquanto o Kafka não é ativado: apenas loga o evento."""

    async def publish(self, topic: str, event: DomainEvent) -> None:
        logger.debug("DomainEvent (não publicado — Kafka ainda não ativado): topic=%s event=%s",
                     topic, event.model_dump())


def get_event_producer() -> EventProducer:
    """Ponto único de obtenção do producer — troque aqui quando o Kafka for ativado."""
    return LoggingEventProducer()
