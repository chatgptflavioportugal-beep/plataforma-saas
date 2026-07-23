# Execução Local

## Frontend

```bash
cd frontend
cp .env.example .env
# Edite .env com VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY, VITE_API_BASE_URL

npm install
npm run dev
# Acesse: http://localhost:3000
```

## Backend Quarkus

```bash
cd backend-quarkus

# Configure as variáveis de ambiente (ou crie um .env no diretório)
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/saas
export QUARKUS_DATASOURCE_USERNAME=postgres
export QUARKUS_DATASOURCE_PASSWORD=postgres
export SUPABASE_JWT_JWKS_URL=https://[project].supabase.co/auth/v1/.well-known/jwks.json
export SUPABASE_JWT_ISSUER=https://[project].supabase.co/auth/v1

./mvnw quarkus:dev
# Acesse: http://localhost:8080
# Swagger UI: http://localhost:8080/q/swagger-ui
```

## PDF Service

O frontend chama o pdf-service diretamente (VITE_PDF_API_URL), sem passar pelo
backend-quarkus. A autorização é feita só pelo ModuleAccessToken emitido pelo
auth-service — não há mais token interno de proxy.

```bash
cd pdf-service
cp .env.example .env
# Edite .env com MODULE_ACCESS_TOKEN_SECRET (mesmo valor usado pelo auth-service)

python -m venv venv
# Windows:
venv\Scripts\activate
# Linux/Mac:
source venv/bin/activate

pip install -r requirements.txt
uvicorn main:app --reload --port 8001
# Acesse: http://localhost:8001
# Swagger: http://localhost:8001/docs
```

## Supabase

### 1. Criar projeto
1. Acesse [supabase.com](https://supabase.com) e crie um projeto
2. Anote: `Project URL`, `anon key`, `JWT Secret`

### 2. Aplicar migrations
No SQL Editor do Supabase, execute em ordem:
```
database/migrations/0001_base_functions.sql
database/migrations/0002_plans.sql
database/migrations/0003_tenants.sql
database/migrations/0004_user_tenants.sql
database/migrations/0005_tenant_subscriptions.sql
database/migrations/0006_audit_logs.sql
database/migrations/0007_usage_records.sql
database/migrations/0008_user_profiles.sql
database/migrations/0009_pdf_jobs.sql
database/migrations/0010_expiration_alerts.sql
```

### 3. Seeds
Execute:
```
database/seeds/0001_plans.sql
```

### 4. Variáveis de ambiente
- `VITE_SUPABASE_URL` = Project URL
- `VITE_SUPABASE_ANON_KEY` = anon key
- `SUPABASE_JWT_JWKS_URL` = `{Project URL}/auth/v1/.well-known/jwks.json`
- `SUPABASE_JWT_ISSUER` = `{Project URL}/auth/v1`

## Executando testes

### Python
```bash
cd pdf-service
pytest tests/
```

### Quarkus
```bash
cd backend-quarkus
./mvnw test
```
