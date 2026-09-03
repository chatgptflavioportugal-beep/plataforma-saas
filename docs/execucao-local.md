# Execução Local

Este guia cobre a execução manual (sem Docker) de cada projeto. Para subir
tudo de uma vez, prefira `docker-compose up --build` (ver README).

## Frontends

Os Micro Frontends remotos (`pdf-frontend`, `whatsapp-frontend`) usam Module
Federation e **não** servem `remoteEntry.js` via `vite dev` — por isso o
script `dev` deles faz `vite build --watch` + `vite preview`, em vez de um
dev server comum. `frontend-host` e `frontend-admin` são apps normais.

### Front Host (login, dashboard, navegação)

```bash
cd frontend-host
cp .env.example .env
# Edite .env com VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY e as *_API_URL

npm install
npm run dev
# Acesse: http://localhost:5100
```

### Frontend Admin (independente, só fala com admin-service)

```bash
cd frontend-admin
cp .env.example .env
npm install
npm run dev
# Acesse: http://localhost:5200
```

### PDF Frontend / WhatsApp Frontend (Micro Frontends)

Precisam do Front Host rodando para serem carregados via Module Federation.
Para iterar isoladamente, defina `VITE_DEV_MODULE_TOKEN` no `.env` local com
um ModuleAccessToken válido.

```bash
cd pdf-frontend       # ou whatsapp-frontend
cp .env.example .env
npm install
npm run dev
# pdf-frontend:       http://localhost:5101
# whatsapp-frontend:  http://localhost:5102
```

## Serviços Quarkus (Java)

`auth-service`, `module-catalog-service`, `profile-service`,
`subscription-service`, `usage-service` e `admin-service` seguem o mesmo
padrão:

```bash
cd auth-service   # ou o serviço desejado
cp .env.example .env
# Edite .env com as credenciais do Supabase e os segredos compartilhados
# (MODULE_ACCESS_TOKEN_SECRET, PROFILE_ACCESS_TOKEN_SECRET)

./mvnw quarkus:dev
```

Portas em dev: auth-service `8082`, module-catalog-service `8083`,
profile-service `8084`, subscription-service `8085`, usage-service `8086`,
admin-service `8087`. Swagger UI disponível em `/q/swagger-ui` de cada um.

**usage-service** depende de `libs/platform-module-security-quarkus`, que
ainda não tem repositório Maven privado — antes do primeiro `quarkus:dev`,
instale a lib no seu `~/.m2` local:

```bash
cd libs/platform-module-security-quarkus
mvn install -DskipTests
```

(o build via Docker/docker-compose já faz isso automaticamente — ver
`usage-service/Dockerfile.dev`.)

## Serviços Python (FastAPI)

`pdf-service` e `whatsapp-service` seguem o mesmo padrão. A autorização é
feita só pelo ModuleAccessToken emitido pelo auth-service, validado pela
biblioteca compartilhada `libs/platform-module-security-python` (instalada em modo
editável via `requirements.txt` — não requer nenhum passo extra).

```bash
cd pdf-service   # ou whatsapp-service
cp config/.env.example .env
# Edite .env com MODULE_ACCESS_TOKEN_SECRET (mesmo valor usado pelo auth-service)

python -m venv venv
# Windows:
venv\Scripts\activate
# Linux/Mac:
source venv/bin/activate

pip install -r requirements.txt
# Para rodar os testes (pytest/httpx/PyJWT — não vão para a imagem de
# produção), instale requirements-dev.txt em vez de requirements.txt:
# pip install -r requirements-dev.txt

uvicorn main:app --reload --port 8001   # pdf-service
# uvicorn main:app --reload --port 8002 # whatsapp-service
# Swagger: http://localhost:8001/docs (ou 8002)
```

## Supabase

### 1. Criar projeto
1. Acesse [supabase.com](https://supabase.com) e crie um projeto
2. Anote: `Project URL`, `anon key`, `JWT Secret`

### 2. Aplicar migrations
No SQL Editor do Supabase (ou via `supabase db push`), execute todos os
arquivos em `database/migrations/`, em ordem numérica.

### 3. Seeds
Execute, em ordem:
```
database/seeds/0001_plans.sql
database/seeds/0002_super_admin.sql
```

### 4. Variáveis de ambiente
- `VITE_SUPABASE_URL` = Project URL
- `VITE_SUPABASE_ANON_KEY` = anon key
- `SUPABASE_JWT_JWKS_URL` = `{Project URL}/auth/v1/.well-known/jwks.json`
- `SUPABASE_JWT_ISSUER` = `{Project URL}/auth/v1`

## Executando testes

### Python
```bash
cd pdf-service        # ou whatsapp-service
pytest tests/
```

### Quarkus
```bash
cd auth-service        # ou o serviço desejado
./mvnw test
```
