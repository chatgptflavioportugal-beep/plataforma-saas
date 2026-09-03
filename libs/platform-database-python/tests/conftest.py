import pytest


class FakeRecord(dict):
    """Duplo de teste para asyncpg.Record — comporta-se como dict, incluindo
    dict(record) e record.keys(), sem precisar de uma conexão real."""


class FakeConnection:
    """Duplo de teste para asyncpg.Connection/Pool: guarda a última query
    executada e devolve linhas pré-programadas via fetch()."""

    def __init__(self, rows_by_sql=None, error_on_sql=None):
        self.rows_by_sql = rows_by_sql or {}
        self.error_on_sql = error_on_sql or {}
        self.calls: list[tuple[str, tuple]] = []

    async def fetch(self, sql, *params):
        self.calls.append((sql, params))
        if sql in self.error_on_sql:
            raise self.error_on_sql[sql]
        return self.rows_by_sql.get(sql, [])


@pytest.fixture
def fake_connection():
    return FakeConnection
