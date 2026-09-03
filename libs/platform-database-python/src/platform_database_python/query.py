from typing import Generic, TypeVar, Union

import asyncpg

from .exceptions import DatabaseQueryException
from .mapper import row_to_object, rows_to_objects

T = TypeVar("T")

ConnectionLike = Union[asyncpg.Connection, asyncpg.Pool]


class NativeQuery(Generic[T]):
    """Uma única execução de native query, montada por DatabaseQuery.native_query.

    Acumula estado (a conexão/pool, o SQL e o resultado esperado) — cada chamada a
    native_query(...) cria uma instância nova; não reutilize entre consultas.
    """

    def __init__(self, conn: ConnectionLike, sql: str, result_type: type[T]):
        self._conn = conn
        self._sql = sql
        self._result_type = result_type

    async def fetch_all(self, *params) -> list[T]:
        rows = await self._execute(*params)
        return rows_to_objects(rows, self._result_type)

    async def fetch_optional(self, *params) -> T | None:
        """0 linhas -> None; 1 linha -> T; 2+ linhas -> DatabaseQueryException
        (nunca descarta o excedente em silêncio)."""
        rows = await self._execute(*params)
        if not rows:
            return None
        if len(rows) > 1:
            raise DatabaseQueryException(
                f"Query esperava no máximo um resultado para {self._result_type.__name__} "
                f"mas retornou {len(rows)} registros. SQL: {self._sql}"
            )
        return row_to_object(rows[0], self._result_type)

    async def fetch_single(self, *params) -> T | None:
        """Alias de fetch_optional — mesma semântica exata."""
        return await self.fetch_optional(*params)

    async def fetch_raw_all(self, *params) -> list[dict]:
        rows = await self._execute(*params)
        return [dict(r) for r in rows]

    async def fetch_raw_optional(self, *params) -> dict | None:
        rows = await self._execute(*params)
        if not rows:
            return None
        if len(rows) > 1:
            raise DatabaseQueryException(
                f"Query esperava no máximo um resultado (raw) mas retornou "
                f"{len(rows)} registros. SQL: {self._sql}"
            )
        return dict(rows[0])

    async def _execute(self, *params) -> list[asyncpg.Record]:
        try:
            return await self._conn.fetch(self._sql, *params)
        except Exception as e:
            raise DatabaseQueryException(
                f"Falha ao executar native query para {self._result_type.__name__} "
                f"({len(params)} parâmetro(s)). SQL: {self._sql}"
            ) from e


class DatabaseQuery:
    """Ponto de entrada da API fluente de native query. Sem estado próprio — a
    conexão/pool do serviço consumidor é informada a cada chamada de
    native_query(...), nunca guardada por esta classe."""

    def native_query(
        self, conn: ConnectionLike, sql: str, result_type: type[T]
    ) -> NativeQuery[T]:
        return NativeQuery(conn, sql, result_type)
