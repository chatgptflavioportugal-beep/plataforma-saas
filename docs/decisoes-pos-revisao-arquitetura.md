# Decisões pós-revisão de arquitetura (pré go-live)

Registro das divergências entre o documento de revisão arquitetural original
e o que o código realmente implementa — e do que ainda depende de uma ação
manual fora deste repositório. Ver também `docs/execucao-local.md`.

## 1. Exceção aprovada: `admin-service` → `subscription-service`

O desenho original proíbe `admin-service` de chamar qualquer outro
microsserviço via REST (ele deveria só acessar o banco diretamente). O código
diverge conscientemente disso em um único ponto:

- `admin-service/src/main/java/com/saas/admin/client/SubscriptionServiceClient.java`
  chama `POST /api/v1/admin/subscriptions/{id}/cancel|reactivate` em
  `subscription-service`, repassando o `Authorization` (JWT Supabase)
  recebido do frontend-admin sem reemitir.
- Motivo: `profile_module_subscriptions` é tabela de propriedade do
  subscription-service (regra de table ownership, reforçada pelos GRANTs de
  `database/migrations/0051_least_privilege_roles.sql`). admin-service
  escrever direto nessa tabela violaria o isolamento de domínio de dados —
  pior do que um hop REST controlado e auditável.
- `subscription-service` **revalida permissão administrativa de verdade**
  (`AdminAuthService`, consulta nativa a `user_profiles`/
  `admin_access_level_permissions`) a partir do mesmo JWT repassado — não há
  confiança cega no header.

**Decisão**: esta é a única exceção aceita à regra "admin-service nunca chama
outro microsserviço". Qualquer necessidade futura de admin-service tocar
tabela de outro domínio deve seguir o mesmo padrão (client REST dedicado +
revalidação de permissão no serviço dono da tabela) — nunca escrita direta
cross-domain, nunca um novo tipo de acoplamento sem essa revalidação.

## 2. usage-service usa Postgres hoje, não Redis

O documento original descreve usage-service como "banco principal Redis,
persistência futura Postgres". Na implementação atual é o oposto: 100%
Postgres (`quarkus-jdbc-postgresql` + Hibernate ORM), com uma interface
`UsageCacheProvider` e uma implementação `NoopCacheProvider` como stub —
Redis nunca foi ativado.

**Decisão**: manter Postgres como fonte de verdade por ora. Se quota/rate
limit precisarem suportar alta concorrência antes ou logo após o go-live,
priorizar a implementação de `UsageCacheProvider` sobre Redis — a interface
já existe, é só trocar a implementação.

## 3. Estratégia de build dos frontends: build-per-ambiente

`frontend-host/Dockerfile` e `frontend-admin/Dockerfile` embutem `VITE_*`
como `ARG`→`ENV` em build-time (Vite resolve `import.meta.env.VITE_*` no
bundle, não em runtime). Isso significa que a imagem Docker resultante fica
presa a um único ambiente (URL do backend, projeto Supabase).

**Decisão**: o pipeline de CI/CD deve fazer **build por ambiente** (uma build
por deploy, com os `--build-arg` do ambiente-alvo), nunca "build once,
promote everywhere". Se isso mudar no futuro, os Dockerfiles desses dois
frontends precisam ser revistos para ler config em runtime (ex.: script de
entrypoint que gera um `env.js` servido antes do bundle).

## 4. Roles de banco de least-privilege (migration 0051/0052) — ativação manual pendente

`database/migrations/0051_least_privilege_roles.sql` já cria as roles
dedicadas por serviço com os GRANTs corretos (cada serviço só
INSERT/UPDATE/DELETE nas tabelas do seu domínio; leitura ampla só para
admin-service). O código dos 6 serviços Quarkus **já está pronto** para usar
essas roles — cada `application.properties` aceita o override via
`<SERVICO>_DB_USERNAME`/`<SERVICO>_DB_PASSWORD` com fallback para a
credencial genérica atual (`docker-compose.yml` já traz essas variáveis
comentadas, prontas para uso).

**O que falta é só um passo manual, fora deste repositório**, que requer
acesso direto ao projeto Supabase — não pode ser automatizado por aqui:

1. Aplicar `0051_least_privilege_roles.sql` (e `0052`, se ainda não aplicada)
   no SQL Editor/CLI do Supabase.
2. Gerar uma senha forte para cada role e aplicá-la:
   ```sql
   ALTER ROLE role_auth_service WITH PASSWORD '<senha-forte-1>';
   ALTER ROLE role_module_catalog_service WITH PASSWORD '<senha-forte-2>';
   ALTER ROLE role_profile_service WITH PASSWORD '<senha-forte-3>';
   ALTER ROLE role_subscription_service WITH PASSWORD '<senha-forte-4>';
   ALTER ROLE role_usage_service WITH PASSWORD '<senha-forte-5>';
   ALTER ROLE role_admin_service WITH PASSWORD '<senha-forte-6>';
   ALTER ROLE role_pdf_service WITH PASSWORD '<senha-forte-7>';
   ALTER ROLE role_whatsapp_service WITH PASSWORD '<senha-forte-8>';
   ```
3. Definir no `.env` raiz (nunca commitar):
   ```
   AUTH_DB_USERNAME=role_auth_service
   AUTH_DB_PASSWORD=<senha-forte-1>
   CATALOG_DB_USERNAME=role_module_catalog_service
   CATALOG_DB_PASSWORD=<senha-forte-2>
   PROFILE_DB_USERNAME=role_profile_service
   PROFILE_DB_PASSWORD=<senha-forte-3>
   SUBSCRIPTION_DB_USERNAME=role_subscription_service
   SUBSCRIPTION_DB_PASSWORD=<senha-forte-4>
   USAGE_DB_USERNAME=role_usage_service
   USAGE_DB_PASSWORD=<senha-forte-5>
   ADMIN_DB_USERNAME=role_admin_service
   ADMIN_DB_PASSWORD=<senha-forte-6>
   ```
   (pdf-service/whatsapp-service usam `DATABASE_URL` — role própria exige
   montar a connection string com `role_pdf_service`/`role_whatsapp_service`.)
4. Descomentar as linhas `*_DB_USERNAME`/`*_DB_PASSWORD` correspondentes no
   `docker-compose.yml` (estão documentadas, uma por serviço) e reiniciar os
   containers.

Até que os passos acima sejam feitos, nada quebra — todos os serviços
continuam na credencial única atual, e o isolamento de dados entre domínios
permanece apenas por convenção de código, não reforçado pelo Postgres.
**Recomendação: fechar isso antes do go-live no Google Cloud.**

## 5. Achado novo, não relacionado às recomendações originais: build de produção quebrado em `auth-service` e `module-catalog-service`

Ao validar as mudanças acima construindo as imagens Docker de produção
(`docker build -f <serviço>/Dockerfile .`, que roda `mvn package` de
verdade — diferente do `Dockerfile.dev`, que nunca compila no momento do
build), **`auth-service` e `module-catalog-service` falham** com:

```
Unsatisfied dependency for type jakarta.persistence.EntityManager
```

Causa: os dois serviços injetam `EntityManager` bruto para SQL nativo, mas
não têm nenhuma classe `@Entity` — e o Hibernate ORM do Quarkus só registra
um `EntityManager` produtor quando encontra ao menos uma entidade mapeada.
`profile-service`, `subscription-service`, `usage-service` e `admin-service`
não têm esse problema porque cada um tem pelo menos uma `@Entity`.

Confirmado como **pré-existente** (reproduzido revertendo temporariamente as
mudanças desta sessão nesses dois `pom.xml` e reconstruindo) — não foi
causado por nenhuma das mudanças acima. Como o `docker-compose.yml` local só
usa `Dockerfile.dev` (que nunca roda `mvn package`), isso nunca falhou em
dev, mas **vai falhar em qualquer deploy real para Cloud Run** desses dois
serviços com o `Dockerfile` de produção atual.

**Não corrigido nesta sessão** (fora do escopo das recomendações pedidas).
Encaminhamentos possíveis: configurar
`quarkus.hibernate-orm.packages=com.saas.auth` (e equivalente no
module-catalog-service) para forçar a criação do persistence unit mesmo sem
entidades, ou avaliar se os dois serviços deveriam ter pelo menos uma
`@Entity` mapeada. Recomendo tratar como bloqueador de go-live — validar com
`docker build -f auth-service/Dockerfile .` depois de qualquer fix.
