import asyncpg

_pool: asyncpg.Pool | None = None


async def init_pool(
    dsn: str,
    *,
    min_size: int = 1,
    max_size: int = 5,
    statement_cache_size: int = 0,
    **kwargs,
) -> None:
    global _pool
    _pool = await asyncpg.create_pool(
        dsn,
        ssl="require",
        # obrigatório para o transaction pooler do Supabase (porta 6543)
        statement_cache_size=statement_cache_size,
        min_size=min_size,
        max_size=max_size,
        **kwargs,
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
