import pytest

from platform_database_python import close_pool, get_pool, init_pool
from platform_database_python import pool as pool_module


@pytest.mark.asyncio
async def test_get_pool_before_init_raises_runtime_error():
    pool_module._pool = None
    with pytest.raises(RuntimeError):
        await get_pool()


@pytest.mark.asyncio
async def test_init_pool_then_get_pool_returns_same_instance(monkeypatch):
    created_with = {}

    class FakePool:
        async def close(self):
            created_with["closed"] = True

    async def fake_create_pool(dsn, **kwargs):
        created_with["dsn"] = dsn
        created_with["kwargs"] = kwargs
        return FakePool()

    monkeypatch.setattr(pool_module.asyncpg, "create_pool", fake_create_pool)

    await init_pool("postgres://example")
    pool = await get_pool()

    assert isinstance(pool, FakePool)
    assert created_with["dsn"] == "postgres://example"
    assert created_with["kwargs"]["statement_cache_size"] == 0
    assert created_with["kwargs"]["min_size"] == 1
    assert created_with["kwargs"]["max_size"] == 5

    await close_pool()
    assert created_with.get("closed") is True
    with pytest.raises(RuntimeError):
        await get_pool()
