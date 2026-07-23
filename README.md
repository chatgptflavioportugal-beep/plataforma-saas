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
Subscription Service e Module Catalog Service (todos Quarkus). Nenhum desses
serviços de módulo (PDF/WhatsApp/...) passa pelo backend-quarkus — o Host só
entrega o ModuleAccessToken; quem chama o backend do módulo é o próprio
Micro Frontend.
```

`backend-quarkus` (monolito original) e `frontend` (SPA original) continuam de pé,
mas o caminho novo para features é sempre: `<módulo>-service` (Python) +
`<módulo>-frontend` (Micro Frontend), cadastrados no Module Catalog Service e
carregados sob demanda pelo Front Host.

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

- Front Host (novo, login/dashboard/navegação): http://localhost:5100
- Frontend Admin (novo, independente): http://localhost:5200
- PDF Frontend / WhatsApp Frontend (Micro Frontends, carregados pelo Host): http://localhost:5101 / http://localhost:5102
- Frontend legado (SPA original, ainda ativo em paralelo): http://localhost:3000
- Quarkus API: http://localhost:8080 — Swagger: http://localhost:8080/q/swagger-ui
- PDF Service: http://localhost:8001/docs · WhatsApp Service: http://localhost:8002/docs
- Admin Service: http://localhost:8087/q/swagger-ui

### 5. Sem Docker — manualmente

Ver [docs/execucao-local.md](docs/execucao-local.md)

---

## Comandos Docker — do dia a dia

### Após alterar código do Frontend

```bash
docker-compose up --build frontend
```

### Após alterar código do Quarkus

```bash
docker-compose up --build backend-quarkus
```

### Após alterar código do Python

```bash
docker-compose up --build pdf-service
```

### Reiniciar um serviço com rebuild

```bash
docker-compose up --build --force-recreate frontend
docker-compose up --build --force-recreate backend-quarkus
docker-compose up --build --force-recreate pdf-service
```

### Ver logs em tempo real

```bash
# Todos os serviços
docker-compose logs -f

# Serviço específico
docker-compose logs -f frontend
docker-compose logs -f backend-quarkus
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
├── frontend/                 SPA original — ainda ativo em paralelo, não modificado
├── backend-quarkus/          Java 21 + Quarkus 3 (monolito original)
├── auth-service/              Emissão/validação de ProfileAccessToken e ModuleAccessToken
├── profile-service/            Tenants, membros, convites, níveis de acesso
├── subscription-service/       Planos, assinaturas de módulo, trials
├── module-catalog-service/     Catálogo de módulos/serviços + resolução de rota
├── usage-service/                Contadores de uso/quota por módulo
├── admin-service/                 Tenants/clientes/usuários admin — só para frontend-admin
├── pdf-service/                     Python 3.11 + FastAPI (módulo PDF)
├── whatsapp-service/                 Python 3.11 + FastAPI (módulo WhatsApp, esqueleto)
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
