# Fluxo de Autenticação

## Visão geral

```
1. Usuário cria conta → supabase.auth.signUp()
2. Supabase envia email de confirmação
3. Usuário confirma email → Supabase cria auth.users
4. Trigger automático cria user_profiles
5. Usuário faz login → supabase.auth.signInWithPassword()
6. Supabase retorna JWT (access_token + refresh_token)
7. Frontend armazena JWT (SDK gerencia automaticamente)
8. Frontend POST /api/v1/public/onboarding → cria tenant + assinatura trial
9. Toda requisição ao Quarkus inclui: Authorization: Bearer <JWT>
10. Quarkus valida JWT, resolve tenant, verifica plano
```

## Fluxo de autorização no Quarkus

```
Request → TenantResolutionFilter:
  1. JWT válido? (SmallRye JWT)          → senão: 401
  2. Path é público? (/public, /q/)     → passa sem verificar tenant
  3. Resolve tenant via X-Tenant-ID     → senão: usa tenant default
  4. Membership ativa?                  → senão: 401
  5. Subscription ativa?                → senão: 402
  
Request → PlanFeatureInterceptor (se @RequiresPlanFeature):
  6. Feature no plano do tenant?        → senão: 403
  
Request → Handler (Resource):
  7. TenantContext disponível via SecurityContext.getUserPrincipal()
  8. Lógica de negócio executada
  9. Auditoria registrada
```

## Área administrativa

O `frontend-admin` (app separado do Front Host, sem prefixo `/admin` nas suas
próprias rotas) só renderiza para usuários com `system_role` igual a
`SUPER_ADMIN` ou `ADMIN_USER` (verificado no `user_profiles` via
`SuperAdminGuard`); dentro dele, cada rota é ainda filtrada por
`AdminPermissionGuard` de acordo com as permissões granulares do
`ADMIN_USER` (SUPER_ADMIN sempre tem acesso total).

No `admin-service`, o `AdminResource` lê o claim `system_role` do JWT e rejeita com 403 se não for SUPER_ADMIN/ADMIN_USER.

Para promover um usuário a SUPER_ADMIN, execute no Supabase:
```sql
UPDATE user_profiles SET system_role = 'SUPER_ADMIN' 
WHERE id = (SELECT id FROM auth.users WHERE email = 'admin@exemplo.com');
```

## Token interno Quarkus → Python

O Python **nunca** recebe o JWT do usuário. O Quarkus envia:
- `X-Internal-Token`: segredo compartilhado
- `X-Tenant-ID`: UUID do tenant
- `X-User-ID`: UUID do usuário

O Python valida apenas o `X-Internal-Token` e processa sem lógica de autenticação de usuário.
