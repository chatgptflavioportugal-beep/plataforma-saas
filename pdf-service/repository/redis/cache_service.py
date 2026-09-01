"""
Fachada de cache sobre o RedisClient — usada por UsageCache/SessionCache/
TokenCache. Enquanto o Redis não é ativado, toda chamada real levanta
RedisNotConfiguredError (ver repository.redis.client); o chamador decide se
trata isso como "sem cache" (fallback) ou propaga o erro.
"""

import json
from typing import Any

from repository.redis.client import RedisClient, get_redis_client


class CacheService:
    def __init__(self, client: RedisClient | None = None):
        self._client = client or get_redis_client()

    async def get_json(self, key: str) -> Any | None:
        raw = await self._client.get(key)
        return json.loads(raw) if raw is not None else None

    async def set_json(self, key: str, value: Any, ttl_seconds: int | None = None) -> None:
        await self._client.set(key, json.dumps(value), ttl_seconds=ttl_seconds)

    async def delete(self, key: str) -> None:
        await self._client.delete(key)

    async def incr(self, key: str, amount: int = 1) -> int:
        return await self._client.incr(key, amount)


class UsageCache(CacheService):
    """Cache de contadores de uso — hoje o Postgres é a fonte de verdade (Usage Service)."""


class SessionCache(CacheService):
    """Cache de sessão/perfil ativo do usuário."""


class TokenCache(CacheService):
    """Cache de ModuleAccessToken/ProfileAccessToken já validados, para evitar redecodificação."""
