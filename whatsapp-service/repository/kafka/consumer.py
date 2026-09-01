"""
Abstração de consumer Kafka — interface pronta, sem broker real ainda.

Quando o Kafka for ativado, uma implementação concreta (ex.: usando
`aiokafka`) deve satisfazer este Protocol, registrando handlers por tópico.
"""

from typing import Awaitable, Callable, Protocol

from repository.kafka.events import DomainEvent

EventHandler = Callable[[DomainEvent], Awaitable[None]]


class EventConsumer(Protocol):
    def subscribe(self, topic: str, handler: EventHandler) -> None: ...

    async def start(self) -> None: ...

    async def stop(self) -> None: ...


class NoopEventConsumer:
    """Implementação padrão enquanto o Kafka não é ativado: não consome nada."""

    def subscribe(self, topic: str, handler: EventHandler) -> None:
        pass

    async def start(self) -> None:
        pass

    async def stop(self) -> None:
        pass


def get_event_consumer() -> EventConsumer:
    """Ponto único de obtenção do consumer — troque aqui quando o Kafka for ativado."""
    return NoopEventConsumer()
