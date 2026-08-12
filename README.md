# SaaS Platform

Plataforma SaaS modular com multi-tenant, controle de planos, free trial e módulos independentes (PDF, WhatsApp, ...), em arquitetura de microsserviços + micro-frontends.

## Arquitetura

```
                    ┌───────────────┐        ┌────────────────────┐
                    │  Front Host    │        │  Frontend Admin     │
                    │  (login,       │        │  (independente,     │
                    │  dashboard,    │        │  só admin-service)  │
                    │  navegação)    │        └──────────┬──────────┘
                    └───────┬────────┘                   │
                            │ Module Federation                     
              ┌─────────────┴─────────────┐               │
              ▼                           ▼               ▼
     ┌─────────────────┐       ┌──────────────────┐  ┌─────────────┐
     │  PDF Frontend    │       │ WhatsApp Frontend │  │Admin Service │
     └────────┬─────────┘       └─────────┬─────────┘  │  (Quarkus)   │
              │ ModuleAccessToken          │            └─────────────┘
              ▼                            ▼
     ┌─────────────────┐       ┌──────────────────┐
     │   PDF Service     │       │ WhatsApp Service  │   ...cada módulo novo:
     │   (FastAPI)        │       │   (FastAPI)        │   backend + micro-frontend
     └─────────────────┘       └──────────────────┘   próprios, cadastrados no
                                                          Module Catalog Service.

Front Host também consome, via Supabase JWT: Auth Service, Profile Service,
Subscription Service e Module Catalog Service (todos Quarkus). Para os
serviços de módulo (PDF/WhatsApp/...), o Host só entrega o ModuleAccessToken;
quem chama o backend do módulo é o próprio Micro Frontend.
```

O monólito Quarkus original (`backend-quarkus`) foi completamente removido do
repositório — toda a responsabilidade de negócio já havia migrado para os
microsserviços dedicados antes da remoção.

O antigo `frontend` (SPA monolítico original) foi removido do repositório —
toda funcionalidade já havia migrado para `frontend-host`/`frontend-admin` +
`<módulo>-service` + `<módulo>-frontend` (Micro Frontend). O caminho para
toda feature nova é sempre esse: módulo cadastrado no Module Catalog Service
e Micro Frontend carregado sob demanda pelo Front Host.

## Pré-requisitos

- Docker Desktop instalado
- Conta no [Supabase](https://supabase.com)

## Setup rápido

### 1. Clone e configure

```bash
git clone <repo>
cd saas-plataforma
cp .env.example .env
# Edite .env com suas credenciais do Supabase
```

### 2. Supabase — aplicar migrations

```bash
# Via CLI do Supabase
supabase db push

# Ou manualmente: copie os arquivos em database/migrations/ 
# e execute na ordem no SQL Editor do Supabase
```

### 3. Seeds (planos)

```bash
# Execute no SQL Editor do Supabase:
# database/seeds/0001_plans.sql
```

### 4. Com Docker Compose (recomendado para dev)

```bash
docker-compose up --build
```

- Front Host (login/dashboard/navegação): http://localhost:5100
- Frontend Admin (independente): http://localhost:5200
- PDF Frontend / WhatsApp Frontend (Micro Frontends, carregados pelo Host): http://localhost:5101 / http://localhost:5102
- Auth Service: http://localhost:8082/q/swagger-ui
- PDF Service: http://localhost:8001/docs · WhatsApp Service: http://localhost:8002/docs
- Admin Service: http://localhost:8087/q/swagger-ui

### 5. Sem Docker — manualmente

Ver [docs/execucao-local.md](docs/execucao-local.md)

---

## Como subir cada projeto individualmente

Todos os serviços sobem juntos com `docker-compose up --build` (passo 4
acima). Para subir **só um** projeto (ex.: depois de mexer no código de um
serviço específico, sem reiniciar os outros 11), use
`docker-compose up --build <nome-do-serviço>` com o nome da tabela abaixo —
o Compose sobe automaticamente qualquer dependência que ainda não esteja no
ar (coluna "Depende de").

### Frontends

| Serviço | Comando | Porta | Depende de |
|---|---|---|---|
| Front Host (login, dashboard, navegação, Module Federation) | `docker-compose up --build frontend-host` | http://localhost:5100 | auth-service, module-catalog-service, profile-service, subscription-service, pdf-frontend, whatsapp-frontend |
| Frontend Admin (independente, só admin-service) | `docker-compose up --build frontend-admin` | http://localhost:5200 | admin-service |
| PDF Frontend (Micro Frontend, remote MF) | `docker-compose up --build pdf-frontend` | http://localhost:5101 | pdf-service |
| WhatsApp Frontend (Micro Frontend, remote MF) | `docker-compose up --build whatsapp-frontend` | http://localhost:5102 | whatsapp-service |

PDF Frontend e WhatsApp Frontend são carregados **sob demanda** pelo Front
Host via Module Federation — abrir só a porta deles isoladamente (sem o
Host no ar) serve para depurar o build do remote, não para navegar a
aplicação como usuário final.

### Backends Quarkus (Java)

| Serviço | Comando | Porta | Swagger UI | Depende de |
|---|---|---|---|---|
| Auth Service (ProfileAccessToken / ModuleAccessToken) | `docker-compose up --build auth-service` | 8082 | http://localhost:8082/q/swagger-ui | — |
| Module Catalog Service (catálogo de módulos/serviços) | `docker-compose up --build module-catalog-service` | 8083 | http://localhost:8083/q/swagger-ui | — |
| Profile Service (tenants, membros, convites) | `docker-compose up --build profile-service` | 8084 | http://localhost:8084/q/swagger-ui | — |
| Subscription Service (planos, assinaturas, trials) | `docker-compose up --build subscription-service` | 8085 | http://localhost:8085/q/swagger-ui | — |
| Usage Service (contadores de uso/quota por módulo) | `docker-compose up --build usage-service` | 8086 | http://localhost:8086/q/swagger-ui | `libs/platform-module-security-quarkus` (instalada automaticamente no build, ver `usage-service/Dockerfile.dev`) |
| Admin Service (tenants/clientes/usuários admin) | `docker-compose up --build admin-service` | 8087 | http://localhost:8087/q/swagger-ui | subscription-service |

### Backends Python (FastAPI)

| Serviço | Comando | Porta | Docs | Depende de |
|---|---|---|---|---|
| PDF Service (módulo PDF: merge, jobs, download) | `docker-compose up --build pdf-service` | 8001 | http://localhost:8001/docs | `libs/platform-module-security` (instalada em modo editável no build) |
| WhatsApp Service (módulo WhatsApp, esqueleto) | `docker-compose up --build whatsapp-service` | 8002 | http://localhost:8002/docs | `libs/platform-module-security` (idem) |

As libs `libs/platform-module-security` e `libs/platform-module-security-quarkus`
não são serviços — não têm entrada própria no `docker-compose.yml` nem
porta exposta. Elas são instaladas dentro da imagem de cada consumidor
(pdf-service/whatsapp-service para a versão Python; usage-service para a
versão Quarkus) no momento do build, então não precisam ser "subidas" à
parte.

### Comandos do dia a dia

```bash
# Rebuild + subir só um serviço (após alterar o código dele)
docker-compose up --build frontend-host
docker-compose up --build auth-service
docker-compose up --build pdf-service

# Reiniciar um serviço específico com rebuild forçado
docker-compose up --build --force-recreate frontend-host
docker-compose up --build --force-recreate pdf-service
```

### Ver logs em tempo real

```bash
# Todos os serviços
docker-compose logs -f

# Serviço específico
docker-compose logs -f frontend-host
docker-compose logs -f pdf-service
```

### Parar tudo

```bash
docker-compose down
```

### Parar e apagar volumes (reset completo)

```bash
docker-compose down -v
```

## Módulos disponíveis

| Módulo | Plano mínimo | Descrição |
|--------|-------------|-----------|
| PDF Merge | Free | Une dois PDFs em um |

## Estrutura do projeto

```
saas-plataforma/
├── frontend-host/          Front Host — login, dashboard, navegação, Module Federation
├── frontend-admin/         Frontend Admin — independente, só fala com admin-service
├── pdf-frontend/            Micro Frontend do módulo PDF (remote MF)
├── whatsapp-frontend/       Micro Frontend do módulo WhatsApp (remote MF)
├── auth-service/              Emissão/validação de ProfileAccessToken e ModuleAccessToken
├── profile-service/            Tenants, membros, convites, níveis de acesso
├── subscription-service/       Planos, assinaturas de módulo, trials
├── module-catalog-service/     Catálogo de módulos/serviços + resolução de rota
├── usage-service/                Contadores de uso/quota por módulo
├── admin-service/                 Tenants/clientes/usuários admin — só para frontend-admin
├── pdf-service/                     Python 3.11 + FastAPI (módulo PDF)
├── whatsapp-service/                 Python 3.11 + FastAPI (módulo WhatsApp, esqueleto)
├── libs/
│   ├── platform-module-security/         Validação do ModuleAccessToken p/ módulos Python
│   │                                       (usada por pdf-service e whatsapp-service)
│   └── platform-module-security-quarkus/ Equivalente Java, validação do ModuleAccessToken
│                                           (usada hoje só pelo usage-service)
├── database/
│   ├── migrations/    Scripts SQL versionados
│   ├── seeds/         Dados iniciais
│   └── docs/          Documentação do banco
├── docs/              Documentação técnica
└── docker-compose.yml Orquestração local
```

## Documentação completa

- [Execução local](docs/execucao-local.md)
- [Fluxo de autenticação](docs/fluxo-autenticacao.md)
- [Módulo PDF](docs/modulo-pdf.md)
- [Como adicionar módulos](docs/como-adicionar-modulos.md)
- [Modelo de dados](database/docs/modelo-dados.md)
