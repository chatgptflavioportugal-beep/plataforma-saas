-- ============================================================
-- LIMPEZA DE AMBIENTE (DEV/STAGING)
-- Preserva : usuário SUPER_ADMIN + todos os planos
-- Remove   : todos os outros usuários, tenants e dados derivados
--
-- Execute no Supabase SQL Editor (função postgres / service_role).
-- ============================================================

BEGIN;

-- ── Segurança: aborta se não houver SUPER_ADMIN ─────────────
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM user_profiles WHERE system_role = 'SUPER_ADMIN'
  ) THEN
    RAISE EXCEPTION 'Nenhum SUPER_ADMIN encontrado. Script cancelado.';
  END IF;

  RAISE NOTICE 'SUPER_ADMIN encontrado — prosseguindo com a limpeza...';
END $$;

-- ── Preview: o que será removido ────────────────────────────
DO $$
DECLARE
  v_admin_id UUID;
BEGIN
  SELECT id INTO v_admin_id
  FROM user_profiles WHERE system_role = 'SUPER_ADMIN' LIMIT 1;

  RAISE NOTICE '─────────────────────────────────────';
  RAISE NOTICE 'Admin preservado : %', v_admin_id;
  RAISE NOTICE 'Usuários a remover: %',
    (SELECT COUNT(*) FROM auth.users WHERE id <> v_admin_id);
  RAISE NOTICE 'Tenants a remover : %',
    (SELECT COUNT(*) FROM tenants);
  RAISE NOTICE 'Planos preservados: %',
    (SELECT COUNT(*) FROM plans);
  RAISE NOTICE '─────────────────────────────────────';
END $$;

-- ── 1. Convites ──────────────────────────────────────────────
-- (FK tenant_id CASCADE, mas apagamos antes para clareza)
DELETE FROM invitations;

-- ── 2. Alertas de expiração ──────────────────────────────────
DELETE FROM expiration_alerts;

-- ── 3. Registros de uso ──────────────────────────────────────
DELETE FROM usage_records;

-- ── 4. Jobs de PDF ───────────────────────────────────────────
DELETE FROM pdf_jobs;

-- ── 5. Logs de auditoria ─────────────────────────────────────
-- (FK tenant_id é SET NULL, não CASCADE — apaga manualmente)
DELETE FROM audit_logs;

-- ── 6. Assinaturas ───────────────────────────────────────────
DELETE FROM tenant_subscriptions;

-- ── 7. Associações usuário-tenant ────────────────────────────
DELETE FROM user_tenants;

-- ── 8. Tenants (empresas e perfis individuais) ───────────────
DELETE FROM tenants;

-- ── 9. Remove todos os usuários exceto o SUPER_ADMIN ─────────
-- Cascade automático: user_profiles, user_tenants (já vazio)
DELETE FROM auth.users
WHERE id NOT IN (
  SELECT id FROM user_profiles WHERE system_role = 'SUPER_ADMIN'
);

-- ── Verificação final ────────────────────────────────────────
SELECT
  tabela,
  total
FROM (
  SELECT 'auth.users restantes'       AS tabela, COUNT(*) AS total FROM auth.users
  UNION ALL
  SELECT 'user_profiles restantes',             COUNT(*)           FROM user_profiles
  UNION ALL
  SELECT 'tenants',                             COUNT(*)           FROM tenants
  UNION ALL
  SELECT 'user_tenants',                        COUNT(*)           FROM user_tenants
  UNION ALL
  SELECT 'tenant_subscriptions',                COUNT(*)           FROM tenant_subscriptions
  UNION ALL
  SELECT 'pdf_jobs',                            COUNT(*)           FROM pdf_jobs
  UNION ALL
  SELECT 'usage_records',                       COUNT(*)           FROM usage_records
  UNION ALL
  SELECT 'audit_logs',                          COUNT(*)           FROM audit_logs
  UNION ALL
  SELECT 'invitations',                         COUNT(*)           FROM invitations
  UNION ALL
  SELECT '── plans (preservados) ──',           COUNT(*)           FROM plans
) t
ORDER BY tabela;

COMMIT;

-- ── Resultado esperado ───────────────────────────────────────
-- auth.users restantes      → 1   (o SUPER_ADMIN)
-- user_profiles restantes   → 1
-- tenants                   → 0
-- user_tenants              → 0
-- tenant_subscriptions      → 0
-- pdf_jobs                  → 0
-- usage_records             → 0
-- audit_logs                → 0
-- invitations               → 0
-- plans (preservados)       → N   (todos mantidos)
