import asyncpg
from config.config import settings

_pool: asyncpg.Pool | None = None


async def init_pool() -> None:
    global _pool
    _pool = await asyncpg.create_pool(
        settings.DATABASE_URL,
        ssl="require",
        statement_cache_size=0,  # obrigatório para o transaction pooler do Supabase (porta 6543)
        min_size=1,
        max_size=5,
    )


async def close_pool() -> None:
    global _pool
    if _pool:
        await _pool.close()
        _pool = None


async def get_pool() -> asyncpg.Pool:
    if _pool is None:
        raise RuntimeError("Pool de banco não inicializado")
    return _pool
