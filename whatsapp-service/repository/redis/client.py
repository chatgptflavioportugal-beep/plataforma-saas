"""
Abstração de cliente Redis — interface pronta, sem conexão real ainda.

Quando o Redis for ativado, uma implementação concreta (ex.: usando o pacote
`redis.asyncio`) deve satisfazer este Protocol e ser injetada onde
`get_redis_client()` é chamado hoje. Nenhum código de módulo deve depender
diretamente de uma biblioteca de Redis — sempre through este client.
"""

from typing import Protocol


class RedisClient(Protocol):
    async def get(self, key: str) -> str | None: ...

    async def set(self, key: str, value: str, ttl_seconds: int | None = None) -> None: ...

    async def delete(self, key: str) -> None: ...

    async def incr(self, key: str, amount: int = 1) -> int: ...


class RedisNotConfiguredError(RuntimeError):
    """Levantado quando o Redis é usado antes de uma implementação real ser ativada."""


class NotConfiguredRedisClient:
    """
    Implementação padrão de `RedisClient` enquanto o Redis não é ativado.
    Qualquer chamada real levanta erro explícito em vez de falhar silenciosamente.
    """

    async def get(self, key: str) -> str | None:
        raise RedisNotConfiguredError("Redis ainda não foi ativado neste serviço.")

    async def set(self, key: str, value: str, ttl_seconds: int | None = None) -> None:
        raise RedisNotConfiguredError("Redis ainda não foi ativado neste serviço.")

    async def delete(self, key: str) -> None:
        raise RedisNotConfiguredError("Redis ainda não foi ativado neste serviço.")

    async def incr(self, key: str, amount: int = 1) -> int:
        raise RedisNotConfiguredError("Redis ainda não foi ativado neste serviço.")


def get_redis_client() -> RedisClient:
    """Ponto único de obtenção do client — troque aqui quando o Redis for ativado."""
    return NotConfiguredRedisClient()
