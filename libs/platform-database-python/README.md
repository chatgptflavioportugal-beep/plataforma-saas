# platform-database-python

Biblioteca compartilhada de acesso a dados para os microsservicos **Python de modulo**
da plataforma (`pdf-service`, `whatsapp-service` e futuros modulos). Concentra o que
hoje seria duplicado em cada servico: subir/derrubar o pool `asyncpg`, executar uma
native query e mapear o resultado para um objeto tipado.

Equivalente Python da `libs/platform-database-quarkus`, adaptada a `asyncpg`: como o
Postgres via `asyncpg` ja devolve tipos nativos Python (UUID, datetime, Decimal, bool),
esta lib **nao** precisa de um `ConversionUtils` (a versao Java existe para lidar tambem
com Oracle).

## Motivacao

Sem a lib, `db/database.py` (pool) e o boilerplate de mapear `asyncpg.Record` para
`dict`/objeto ficam duplicados em cada servico Python, sem nenhuma checagem de "no
maximo um resultado":

```python
pool = await get_pool()
async with pool.acquire() as conn:
    row = await conn.fetchrow(sql, tenant_id)
    return dict(row) if row else None
```

Com a lib, o pool e compartilhado e a query declara so o SQL, os parametros e o TO
esperado:

```python
user = await database_query.native_query(pool, sql, UserTenantTO).fetch_optional(tenant_id)
```

## Instalacao

Ainda nao existe um indice PyPI privado. Enquanto isso, referencie o caminho relativo
no `requirements.txt` do servico consumidor:

```
-e ../libs/platform-database-python
```

Quando um indice PyPI privado estiver disponivel, a mesma dependencia passa a ser
`pip install platform-database-python` ou `pip install git+ssh://...#subdirectory=libs/platform-database-python`.

Em Docker, o build do servico consumidor precisa copiar o codigo-fonte desta lib para
dentro do contexto de build (mesmo principio do `platform-module-security-python` ja
usado em `pdf-service`/`whatsapp-service`):

```dockerfile
COPY libs/platform-database-python /libs/platform-database-python
```

## Uso — pool (`pool`)

```python
from contextlib import asynccontextmanager
from fastapi import FastAPI
from platform_database_python import init_pool, close_pool
from config.config import settings

@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_pool(settings.DATABASE_URL)
    yield
    await close_pool()
```

`init_pool(dsn, *, min_size=1, max_size=5, statement_cache_size=0, **kwargs)` sempre
usa `ssl="require"` e mantem `statement_cache_size=0` por padrao — **obrigatorio** para
o transaction pooler do Supabase (porta 6543). A lib nao le `.env` sozinha: o `dsn` e
sempre informado pelo servico consumidor (que continua dono do seu `Settings`), para
nao duplicar um segundo mecanismo de configuracao para a mesma variavel.

Nos DAOs, `get_pool()` devolve o pool ja inicializado (`asyncpg.Pool`), que tambem serve
diretamente como `conn` na API de query abaixo (`Pool.fetch(...)` adquire e libera a
conexao sozinho).

## Uso — API fluente de native query (`query`)

### 1. Declarar o TO

Campos declarados com `column("<alias ou nome da coluna>")` — mesmo papel do `@Column`
Java. Comparacao de nome de coluna e case-insensitive:

```python
from dataclasses import dataclass
from platform_database_python import column

@dataclass
class UserTenantTO:
    tenant_id: str = column("tenant_id")
    role: str = column("role")
```

### 2. Consultar

```python
from platform_database_python import DatabaseQuery

database_query = DatabaseQuery()

async def find_by_user_and_tenant(pool, user_id, tenant_id) -> UserTenantTO | None:
    return await database_query.native_query(
        pool,
        "SELECT tenant_id, role FROM user_tenants WHERE user_id = $1 AND tenant_id = $2",
        UserTenantTO,
    ).fetch_optional(user_id, tenant_id)


async def find_all_for_tenant(pool, tenant_id) -> list[UserTenantTO]:
    return await database_query.native_query(
        pool,
        "SELECT tenant_id, role FROM user_tenants WHERE tenant_id = $1",
        UserTenantTO,
    ).fetch_all(tenant_id)
```

### Parametros — desvio deliberado da API Java

`asyncpg` usa parametros posicionais (`$1, $2, ...`), nao nomeados como JPA (`:nome`).
Em vez de um `.set_parameter(nome, valor)` encadeado — que arriscaria dessincronizar a
ordem posicional — os parametros sao passados de uma vez no metodo terminal
(`fetch_all(*params)`, `fetch_optional(*params)`, etc). O `asyncpg` faz o binding
preservando o tipo original do valor; a lib nunca faz `str(valor)`.

### Metodos de execucao

| Metodo | Retorno | Comportamento |
|---|---|---|
| `fetch_all(*params)` | `list[T]` | Mapeia cada linha para `T`. |
| `fetch_optional(*params)` | `T \| None` | 0 linhas -> `None`; 1 linha -> `T`; 2+ linhas -> `DatabaseQueryException` (nunca descarta o excedente em silencio). |
| `fetch_single(*params)` | `T \| None` | Alias de `fetch_optional` — mesma semantica exata. |
| `fetch_raw_all(*params)` | `list[dict]` | Uso secundario: resultado bruto (coluna -> valor), sem TO. |
| `fetch_raw_optional(*params)` | `dict \| None` | Equivalente bruto de `fetch_optional`. |

### Thread-safety

`DatabaseQuery` nao guarda estado (nem a conexao/pool, recebida a cada chamada) — uma
unica instancia pode ser compartilhada por todos os DAOs do servico. Ja `NativeQuery`
acumula estado de **uma** consulta e por isso nao deve ser reutilizada entre chamadas:
cada `native_query(...)` cria uma instancia nova.

## Tratamento de excecoes

Duas excecoes proprias, nunca engolidas silenciosamente:

- **`DatabaseQueryException`** — falha na execucao da native query (SQL/parametro
  invalido) ou quando `fetch_optional`/`fetch_single`/`fetch_raw_optional` recebem mais
  de um registro. A mensagem inclui o TO esperado e o SQL.
- **`DatabaseMappingException`** — falha ao popular o TO: coluna esperada por um campo
  `column(...)` ausente na linha retornada, ou construtor do TO rejeitando os valores
  mapeados.

## Testes

```bash
cd libs/platform-database-python
pip install -e ".[dev]"
pytest
```

Todos os testes usam duplos de teste (`FakeConnection`/`FakeRecord` em `tests/conftest.py`)
ou mocks de `asyncpg` — nenhum exige um Postgres real.

## Compatibilidade

Python 3.11+, `asyncpg`, PostgreSQL (Supabase). Sem dependencia de FastAPI/framework —
funciona com qualquer conexao/pool `asyncpg`.

## Proximas extensoes

Estrutura preparada para outras funcionalidades de acesso a dados hoje duplicadas entre
microsservicos Python (ex.: paginacao de native query, builders de filtro dinamico).
Adicionar como novos modulos dentro desta mesma lib — nao criar uma lib nova por
funcionalidade.
