# Modelo de Dados

## Diagrama de entidades

```
auth.users (Supabase gerenciado)
    │
    ├── user_profiles (1:1) — perfil estendido, system_role
    │
    └── user_tenants (N:N) ── tenants
                                │
                                ├── plans (N:1)
                                ├── tenant_subscriptions
                                ├── audit_logs
                                ├── usage_records
                                ├── pdf_jobs
                                └── expiration_alerts
```

## Tabelas

| Tabela | Descrição |
|--------|-----------|
| `auth.users` | Gerenciada pelo Supabase. Fonte de verdade de identidade. |
| `user_profiles` | Dados estendidos do usuário. `system_role` define SUPER_ADMIN. |
| `tenants` | Empresas/organizações. Base do multi-tenant. |
| `user_tenants` | Relação usuário ↔ tenant com papel (owner/admin/member). |
| `plans` | Planos disponíveis com features em JSONB. |
| `tenant_subscriptions` | Assinatura ativa do tenant. Controla trial/ativo/suspenso. |
| `audit_logs` | Log imutável de todas as ações sensíveis. |
| `usage_records` | Registros de uso para controle de cotas por período. |
| `pdf_jobs` | Jobs do módulo PDF (merge A + B). |
| `expiration_alerts` | Controle de alertas de expiração já enviados. |

## RLS

Toda tabela com dados de tenant tem RLS habilitado. O isolamento funciona em duas camadas:
1. **Quarkus**: filtra por `tenant_id` em todas as queries (primeira linha)
2. **Supabase RLS**: políticas impedem vazamento mesmo em acesso direto ao banco (segunda linha)
