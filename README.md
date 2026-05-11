# SaaS Platform

Plataforma SaaS modular com multi-tenant, controle de planos, free trial e módulo de PDF.

## Arquitetura

```
┌─────────────────────────────────────────────────────────┐
│                   FRONTEND (React 18)                    │
│          /app (empresas)   |   /admin (SUPER_ADMIN)     │
└──────────────────────┬──────────────────────────────────┘
                       │ JWT (Supabase Auth)
         ┌─────────────▼───────────────┐
         │    SUPABASE AUTH             │
         └─────────────┬───────────────┘
                       │ JWT
┌──────────────────────▼──────────────────────────────────┐
│              BACKEND QUARKUS (Gateway)                   │
│  • Valida JWT   • Resolve tenant   • Verifica plano     │
│  • Auditoria    • Trial / Bloqueio • Proxy para Python  │
└────────────┬──────────────────────┬─────────────────────┘
             │                      │
    ┌────────▼────────┐   ┌─────────▼──────────┐
    │  SUPABASE DB    │   │  BACKEND PYTHON      │
    │  PostgreSQL+RLS │   │  FastAPI + pypdf     │
    └─────────────────┘   └────────────────────┘
```

## Pré-requisitos

- Node.js 20+
- Java 21 (JDK)
- Python 3.11+
- Maven 3.9+
- Docker + Docker Compose (opcional, para rodar tudo junto)
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

- Frontend: http://localhost:3000
- Quarkus API: http://localhost:8080
- Python API: http://localhost:8001
- Swagger Quarkus: http://localhost:8080/q/swagger-ui
- Swagger Python: http://localhost:8001/docs

### 5. Sem Docker — manualmente

Ver [docs/execucao-local.md](docs/execucao-local.md)

## Módulos disponíveis

| Módulo | Plano mínimo | Descrição |
|--------|-------------|-----------|
| PDF Merge | Free | Une dois PDFs em um |

## Estrutura do projeto

```
saas-plataforma/
├── frontend/          React 18 + TypeScript + Vite
├── backend-quarkus/   Java 21 + Quarkus 3
├── backend-python/    Python 3.11 + FastAPI
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
