# Como Adicionar Novos Módulos

Este guia explica o passo a passo para criar um novo módulo na plataforma.

## Exemplo: criar módulo "Assinatura Digital"

### 1. Database — migration

Crie `database/migrations/0011_signature_jobs.sql`:

```sql
CREATE TABLE signature_jobs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES auth.users(id),
    status      TEXT NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending', 'processing', 'signed', 'failed')),
    -- campos específicos do módulo
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_signature_jobs_tenant_id ON signature_jobs(tenant_id);
ALTER TABLE signature_jobs ENABLE ROW LEVEL SECURITY;
-- RLS policies ...
```

### 2. Database — seed (se necessário)

Adicione a feature ao plano em `database/seeds/0001_plans.sql`:

```sql
UPDATE plans SET features = features || '{"signature.sign": true}'
WHERE code IN ('pro', 'enterprise');
```

### 3. Backend Python (serviço dedicado do novo módulo)

Cada módulo tem seu próprio microsserviço Python (ex: `pdf-service`, `whatsapp-service`). Para "Assinatura Digital", crie `signature-service/` seguindo a mesma estrutura de `pdf-service/` (`routes/`, `security/`, `middleware/`, `services/`, `validators/`, `schemas/`, `repository/`, `storage/`, `permissions/`, `health/`). Registre o router em `main.py`:

```python
from routers import signature_router
app.include_router(signature_router.router)
```

### 4. Backend Quarkus

**Entity**: `src/main/java/com/saas/entity/SignatureJob.java`

```java
@Entity
@Table(name = "signature_jobs")
public class SignatureJob extends BaseEntity {
    // campos
}
```

**Resource**: `src/main/java/com/saas/resource/SignatureResource.java`

```java
@Path("/api/v1/signature")
@Authenticated
public class SignatureResource {

    @POST
    @Path("/sign")
    @RequiresPlanFeature("signature.sign")  // ← chave do plano
    public Response sign(...) { ... }
}
```

**Service**, **Repository**: seguir padrões em [padroes-backend-quarkus](../.claude-base/context/padroes-backend-quarkus.md)

### 5. Frontend

**Hook**: `src/hooks/useSignature.ts` (useQuery + useMutation com React Query)

**Página**: `src/pages/app/signature/SignaturePage.tsx`

**Rota**: adicionar em `src/router/routes.tsx`:

```tsx
<Route path="signature" element={<SignaturePage />} />
```

**NavItem**: adicionar em `AppLayout.tsx`:

```tsx
{ path: '/app/signature', label: 'Assinatura Digital' },
```

### 6. Checklist antes de considerar completo

- [ ] Migration criada e testada localmente (`supabase db reset`)
- [ ] RLS habilitada na tabela
- [ ] Feature adicionada aos planos relevantes
- [ ] Endpoint com `@RequiresPlanFeature` correto
- [ ] Auditoria registrada em ações sensíveis
- [ ] Frontend trata 402 (trial expirado) e 403 (feature bloqueada)
- [ ] Testes de endpoint (401 sem JWT, 402 trial, 403 plano, cross-tenant)
