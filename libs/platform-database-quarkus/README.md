# platform-database-quarkus

Biblioteca compartilhada de acesso a dados para os microsservicos **Quarkus** da
plataforma. Concentra o que hoje seria duplicado em cada DAO: executar uma native query,
mapear o resultado para um objeto tipado e converter os tipos que Oracle/PostgreSQL
devolvem para o tipo Java esperado.

Nunca sobe um `EntityManagerFactory` nem gerencia conexao/transacao — quem fornece o
`EntityManager` e o `Hibernate ORM` e sempre o servico consumidor. `GenericTOMapper`,
`TupleTOMapper` e `ConversionUtils` sao utilitarios estaticos, sem CDI; ja `DatabaseQuery`
e um bean CDI (`@ApplicationScoped`) para poder ser injetado com `@Inject` como qualquer
outro bean da plataforma — por isso a lib depende de `quarkus-arc` e gera seu proprio
`META-INF/jandex.idx` (mesmo principio do `libs/platform-module-security-quarkus`), para o
ArC do servico consumidor descobrir o bean.

## Motivacao

Sem a lib, um DAO que roda native query fica assim:

```java
List<Object[]> rows = em.createNativeQuery("SELECT id, name, email FROM users ...").getResultList();
Object[] row = rows.get(0);
Long id = ((Number) row[0]).longValue();
String name = (String) row[1];
String email = (String) row[2];
```

Ou, num passo intermediario ja usando `Tuple`, ainda verboso e repetido em cada DAO:

```java
List<Tuple> result = entityManager.createNativeQuery(sql, Tuple.class)
        .setParameter("userId", userId)
        .getResultList();

return result.stream()
        .map(tuple -> TupleTOMapper.map(tuple, UserTenantTO.class))
        .findFirst();
```

Com a lib, o DAO declara so o SQL, os parametros e o TO esperado — nada de `Tuple`,
`Object[]`, `stream().map(...).findFirst()` nem chamada direta ao mapper:

```java
Optional<UserTenantTO> user = databaseQuery
        .nativeQuery(em, sql, UserTenantTO.class)
        .setParameter("userId", userId)
        .getOptionalResult();
```

## Instalacao

Ainda nao existe um repositorio Maven privado. Enquanto isso:

```bash
cd libs/platform-database-quarkus
mvn install -DskipTests
```

Isso publica `com.saas:platform-database-quarkus:1.0.0-SNAPSHOT` no seu `~/.m2` local. O servico
consumidor entao declara a dependencia normalmente:

```xml
<dependency>
    <groupId>com.saas</groupId>
    <artifactId>platform-database-quarkus</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Em Docker, o build multi-stage do servico consumidor precisa copiar o codigo-fonte desta
lib para dentro do contexto de build e rodar `mvn install -N -DskipTests` nela **antes**
de compilar o servico — mesmo principio do `libs/platform-module-security-quarkus` usado
em `usage-service/Dockerfile`/`Dockerfile.dev` (contexto de build do `docker-compose.yml`
precisa ser a raiz do repositorio, nao a pasta do servico).

## Padrao arquitetural

Todo microsservico Quarkus da plataforma segue:

```
Resource → Negocio → DAO → Banco
```

E o DAO nunca devolve `Entity`, `Object[]`, `Tuple` ou resultado bruto para a camada de
Negocio — devolve um **TO** (Transfer Object da camada de dados). A camada de Negocio
converte esse TO em **DTO**, que e o unico tipo que sai pela API via Resource:

```
DAO → TO → Negocio → DTO → Resource
```

A biblioteca cobre exatamente a fatia `Native Query → TO`:

```
DAO
 │  SQL + parametros + TO esperado
 ▼
DatabaseQuery / NativeQuery
 │
 ▼
JPA/Hibernate (EntityManager fornecido pelo DAO)
 │
 ▼
GenericTOMapper (metadata cacheada por Class) + ConversionUtils
 │
 ▼
TO
```

## Uso — API fluente de native query (`query`)

### 1. Injetar o `DatabaseQuery`

`DatabaseQuery` e um bean CDI sem estado — injete com `@Inject` como qualquer outro bean da
plataforma, sem construir nada manualmente. O `EntityManager` do servico (tambem injetado,
igual a hoje) e passado a cada chamada de `nativeQuery(...)`, nao no momento da injecao:

```java
@ApplicationScoped
public class UserTenantDAO {

    @Inject
    EntityManager em;

    @Inject
    DatabaseQuery databaseQuery;

    // ...
}
```

Por nao guardar o `EntityManager` (nem qualquer outro estado), a mesma instancia de
`DatabaseQuery` — um singleton de aplicacao — e compartilhada por todos os DAOs do servico
sem qualquer risco de concorrencia; nunca cria conexao/`EntityManager` proprio nem gerencia
transacao (isso continua sendo responsabilidade do DAO/Negocio via `@Transactional`, como
hoje).

### 2. Declarar o TO

Igual a antes — campos anotados com `@Column(name = "<alias ou nome da coluna>")`; os
demais sao ignorados. Campos `@Column` herdados de uma superclasse tambem sao mapeados. O
TO precisa de um construtor sem argumentos (a lib popula os campos via reflection, nao
pelo construtor):

```java
public class UserTenantTO {

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "role")
    private String role;

    // getters
}
```

### 3. Consultar

```java
public Optional<UserTenantTO> findByUserAndTenant(UUID userId, UUID tenantId) {
    return databaseQuery
            .nativeQuery(em, """
                    SELECT ut.tenant_id::text AS tenant_id, ut.role AS role FROM user_tenants ut
                    WHERE ut.user_id = :userId AND ut.tenant_id = :tenantId AND ut.is_active = TRUE
                    """, UserTenantTO.class)
            .setParameter("userId", userId)
            .setParameter("tenantId", tenantId)
            .getOptionalResult();
}

public List<UserTenantTO> findAllForTenant(UUID tenantId) {
    return databaseQuery
            .nativeQuery(em, "SELECT tenant_id, role FROM user_tenants WHERE tenant_id = :tenantId",
                    UserTenantTO.class)
            .setParameter("tenantId", tenantId)
            .getResultList();
}
```

O DAO nunca precisa conhecer `Tuple`, `Object[]`, `stream().map(...).findFirst()` nem
chamar `TupleTOMapper`/`GenericTOMapper` diretamente — isso e implementacao interna de
`NativeQuery`.

### `setParameter` — sem conversao para String

```java
.setParameter("userId", userId)       // UUID
.setParameter("createdAt", createdAt) // LocalDateTime
.setParameter("active", active)       // Boolean
```

O valor e passado **diretamente** para `jakarta.persistence.Query#setParameter(String,
Object)` — o Hibernate/JPA cuida do binding preservando o tipo original. A lib nunca faz
`valor.toString()` s so para depois converter de volta.

### Metodos de execucao

| Metodo | Retorno | Comportamento |
|---|---|---|
| `getResultList()` | `List<T>` | Mapeia cada linha para `T`. |
| `getOptionalResult()` | `Optional<T>` | 0 linhas → `Optional.empty()`; 1 linha → `Optional.of(TO)`; 2+ linhas → `DatabaseQueryException` (nunca descarta o excedente em silencio). |
| `getSingleResult()` | `Optional<T>` | Alias de `getOptionalResult()` — mesma semantica exata; existe so para quem prefere o nome mais proximo de `EntityManager#getSingleResult`. |
| `getRawResultList()` | `List<Map<String,Object>>` | Uso secundario: resultado bruto (alias → valor), sem TO. |
| `getRawSingleResult()` | `Optional<Map<String,Object>>` | Equivalente bruto de `getOptionalResult()`. |

### Thread-safety

`DatabaseQuery` nao guarda estado algum (nem o `EntityManager`, recebido a cada chamada) —
por isso e seguro como singleton de aplicacao, compartilhado por todos os DAOs do servico.
Ja `NativeQuery` acumula estado mutavel (os parametros de **uma** consulta) e por isso
**nao** e thread-safe nem reutilizavel: cada chamada a `nativeQuery(...)` cria uma instancia
nova, e o uso esperado e montar e executar numa unica cadeia de chamadas, como nos exemplos
acima. Nunca guarde um `NativeQuery` num campo para reusar entre chamadas.

## Uso — mappers de baixo nivel (`mapper`)

`NativeQuery` usa esses mappers internamente; DAOs normalmente nao precisam chama-los
direto, mas eles continuam publicos para quem tiver um `Tuple`/`Map` de outra origem (ex.:
JDBC puro):

```java
UserTenantTO to = TupleTOMapper.map(tuple, UserTenantTO.class);
List<UserTenantTO> tos = GenericTOMapper.mapList(rows, UserTenantTO.class);
```

```
Tuple → Map<String,Object> → GenericTOMapper → TO
```

O alias/nome de coluna e comparado **sem diferenciar maiusculas/minusculas** — Oracle
costuma devolver em maiusculo, PostgreSQL em minusculo; o TO nao precisa saber de qual
banco veio.

### Cache de metadata

A reflection sobre cada `Class<T>` (campos `@Column`, inclusive herdados, e o construtor
sem argumentos) e feita **uma unica vez** por classe e cacheada num
`ConcurrentHashMap<Class<?>, TOMetadata>` interno ao `GenericTOMapper` — uma consulta com
milhares de linhas nao repete o scan de anotacoes a cada linha, so a conversao de valor por
campo (via `ConversionUtils`).

### Conversoes cobertas por `ConversionUtils`

`String`, `Long`, `Integer`, `Double`, `Float`, `Boolean`, `BigDecimal`, `LocalDate`,
`LocalDateTime`, `OffsetDateTime` (assume UTC ao converter de `Timestamp`/`Date`, que nao
guardam fuso), `java.util.Date`, `java.sql.Timestamp`, `Enum`, `UUID`.

Inclui os casos comuns de Oracle, onde qualquer coluna numerica (inclusive `NUMBER(1)`
usado como boolean) chega como `BigDecimal`: `BigDecimal → Long`, `BigDecimal → Integer`,
`BigDecimal → Boolean` (`0`/`1`).

## Regra obrigatoria

Nunca retornar `Entity`, TO, `Object[]` ou `Tuple` diretamente de um Resource. So `DTO` sai
pela API.

## Tratamento de excecoes

Duas excecoes proprias, nunca engolidas silenciosamente:

- **`DatabaseQueryException`** (pacote `query`/`exception`) — falha na execucao da native
  query (SQL invalido, parametro incompativel) ou quando `getOptionalResult()`/
  `getSingleResult()` recebem mais de um registro. A mensagem inclui o TO esperado, o SQL e
  os nomes dos parametros aplicados.
- **`DatabaseMappingException`** (pacote `mapper`/`exception`) — falha ao popular o TO: TO
  sem construtor sem argumentos, campo que nao aceita o valor convertido, ou conversao nao
  suportada por `ConversionUtils`. A mensagem inclui a classe do TO e o campo problematico.

Nenhum dos dois e capturado internamente so para devolver `Optional.empty()` — um erro real
sempre propaga.

## Testes

```bash
mvn test
```

- `GenericTOMapperTest` / `ConversionUtilsTest`: mapeamento a partir de `Map` (TO simples,
  TO com heranca, campo sem `@Column`, coluna sem campo correspondente, valor nulo) e cada
  conversao de tipo, incluindo os casos de Oracle.
- `TupleTOMapperTest`: adaptador `Tuple → Map` com um duplo de teste de `Tuple`
  (`FakeTuple`), sem subir `EntityManager`.
- `NativeQueryTest` / `DatabaseQueryTest`: `EntityManager`/`Query` mockados (Mockito) —
  cobrem `getResultList()`, `getOptionalResult()` (vazio, unico, multiplos registros →
  excecao), `setParameter` preservando o tipo original do valor, e que cada
  `nativeQuery(...)` cria uma instancia independente.

## Compatibilidade

Java 17+, Quarkus, Hibernate ORM, JPA/Jakarta Persistence, PostgreSQL, Oracle. Dependencias
de compilacao: `quarkus-arc` (CDI, para o bean `DatabaseQuery`) e `jakarta.persistence-api`
(`provided`, ja presente em qualquer servico com Hibernate ORM).

## Proximas extensões

Estrutura preparada para outras funcionalidades de acesso a dados que hoje sao duplicadas
entre microsservicos (ex.: paginacao de native query, builders de filtro dinamico).
Adicionar como novos pacotes dentro desta mesma lib — nao criar uma lib nova por
funcionalidade.
