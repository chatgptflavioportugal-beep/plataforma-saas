-- ============================================================
-- LIMPEZA DE AMBIENTE DE TESTES
--
-- Remove   : todos os dados de clientes e perfis
-- Preserva : SUPER_ADMIN, módulos, serviços,
--            grupos de serviços, planos, versões de planos,
--            vínculos plano-módulo, limites, níveis de acesso
--            administrativos e permissões administrativas.
--
-- Execute no Supabase SQL Editor com service_role (postgres).
-- ============================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- PASSO 0: Salvaguarda — aborta se não houver admin cadastrado
-- ─────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admins  BIGINT;
    v_clients BIGINT;
    v_tenants BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_admins
    FROM public.user_profiles
    WHERE system_role IN ('SUPER_ADMIN');

    IF v_admins = 0 THEN
        RAISE EXCEPTION
            'Nenhum usuário administrativo encontrado (SUPER_ADMIN). '
            'Execução abortada para segurança.';
    END IF;

    SELECT COUNT(*) INTO v_clients
    FROM auth.users
    WHERE id NOT IN (
        SELECT id FROM public.user_profiles
        WHERE system_role IN ('SUPER_ADMIN')
    );

    SELECT COUNT(*) INTO v_tenants FROM public.tenants;

    RAISE NOTICE '══════════════════════════════════════════';
    RAISE NOTICE ' LIMPEZA DE DADOS DE CLIENTES';
    RAISE NOTICE '══════════════════════════════════════════';
    RAISE NOTICE ' Admins preservados (SUPER_ADMIN) : %', v_admins;
    RAISE NOTICE ' Usuários clientes a remover                 : %', v_clients;
    RAISE NOTICE ' Tenants (empresas + perfis) a remover       : %', v_tenants;
    RAISE NOTICE '──────────────────────────────────────────';
END;
$$;

-- ─────────────────────────────────────────────────────────────
-- FASE 1 — Dados de jobs e monitoramento
-- Tabelas 100% de clientes (tenant_id sempre de cliente).
-- ─────────────────────────────────────────────────────────────

DELETE FROM public.pdf_jobs;
DELETE FROM public.expiration_alerts;
DELETE FROM public.audit_logs;
DELETE FROM public.usage_records;

-- ─────────────────────────────────────────────────────────────
-- FASE 2 — Permissões dos níveis de acesso de perfis
-- ─────────────────────────────────────────────────────────────

DELETE FROM public.profile_access_level_permissions;
DELETE FROM public.profile_access_level_admin_permissions;

-- ─────────────────────────────────────────────────────────────
-- FASE 3 — Assinaturas e módulos contratados
-- ─────────────────────────────────────────────────────────────

DELETE FROM public.profile_module_subscriptions;
DELETE FROM public.tenant_subscriptions;

-- ─────────────────────────────────────────────────────────────
-- FASE 4 — Convites e vínculos de membros
--
-- ATENÇÃO: invitations.invited_by → auth.users sem ON DELETE CASCADE.
-- Convites devem ser removidos ANTES de auth.users (fase 8).
-- ─────────────────────────────────────────────────────────────

DELETE FROM public.invitations;
DELETE FROM public.user_tenants;

-- ─────────────────────────────────────────────────────────────
-- FASE 5 — Níveis de acesso de perfis empresariais
-- ─────────────────────────────────────────────────────────────

DELETE FROM public.profile_access_levels;

-- ─────────────────────────────────────────────────────────────
-- FASE 6 — Tenants (empresas e perfis individuais)
-- ─────────────────────────────────────────────────────────────

DELETE FROM public.tenants;

-- ─────────────────────────────────────────────────────────────
-- FASE 7 — Perfis de usuários clientes
-- Preserva system_role IN ('SUPER_ADMIN').
-- ─────────────────────────────────────────────────────────────

DELETE FROM public.user_profiles
WHERE system_role NOT IN ('SUPER_ADMIN');

-- ─────────────────────────────────────────────────────────────
-- FASE 8 — Usuários clientes no Supabase Auth
--
-- REQUER service_role / postgres — não funciona com anon/user.
-- Filtra pelo system_role do perfil para garantir que apenas
-- usuários administrativos sejam preservados.
-- ─────────────────────────────────────────────────────────────

DELETE FROM auth.users
WHERE id NOT IN (
    SELECT id FROM public.user_profiles
    WHERE system_role IN ('SUPER_ADMIN')
);

-- ─────────────────────────────────────────────────────────────
-- VERIFICAÇÃO FINAL
-- ─────────────────────────────────────────────────────────────

SELECT tabela, total, esperado
FROM (
    SELECT 'tenants'                            AS tabela, COUNT(*) AS total, '0'::text AS esperado FROM public.tenants
    UNION ALL
    SELECT 'user_tenants',                       COUNT(*), '0' FROM public.user_tenants
    UNION ALL
    SELECT 'tenant_subscriptions',               COUNT(*), '0' FROM public.tenant_subscriptions
    UNION ALL
    SELECT 'profile_module_subscriptions',       COUNT(*), '0' FROM public.profile_module_subscriptions
    UNION ALL
    SELECT 'invitations',                        COUNT(*), '0' FROM public.invitations
    UNION ALL
    SELECT 'profile_access_levels',              COUNT(*), '0' FROM public.profile_access_levels
    UNION ALL
    SELECT 'profile_access_level_permissions',   COUNT(*), '0' FROM public.profile_access_level_permissions
    UNION ALL
    SELECT 'profile_access_level_admin_perms',   COUNT(*), '0' FROM public.profile_access_level_admin_permissions
    UNION ALL
    SELECT 'pdf_jobs',                           COUNT(*), '0' FROM public.pdf_jobs
    UNION ALL
    SELECT 'expiration_alerts',                  COUNT(*), '0' FROM public.expiration_alerts
    UNION ALL
    SELECT 'audit_logs',                         COUNT(*), '0' FROM public.audit_logs
    UNION ALL
    SELECT 'usage_records',                      COUNT(*), '0' FROM public.usage_records
    UNION ALL
    SELECT '── auth.users (admins restantes)',    COUNT(*), '≥1' FROM auth.users
    UNION ALL
    SELECT '── user_profiles (admins restantes)', COUNT(*), '≥1' FROM public.user_profiles
    UNION ALL
    SELECT '── plans (preservados)',              COUNT(*), 'N'  FROM public.plans
    UNION ALL
    SELECT '── plan_version_modules',             COUNT(*), 'N'  FROM public.plan_version_modules
    UNION ALL
    SELECT '── plan_version_module_limits',       COUNT(*), 'N'  FROM public.plan_version_module_limits
    UNION ALL
    SELECT '── platform_modules',                 COUNT(*), 'N'  FROM public.platform_modules
    UNION ALL
    SELECT '── platform_module_services',         COUNT(*), 'N'  FROM public.platform_module_services
    UNION ALL
    SELECT '── platform_module_service_groups',   COUNT(*), 'N'  FROM public.platform_module_service_groups
    UNION ALL
    SELECT '── admin_access_levels',              COUNT(*), 'N'  FROM public.admin_access_levels
    UNION ALL
    SELECT '── admin_access_level_permissions',   COUNT(*), 'N'  FROM public.admin_access_level_permissions
) t
ORDER BY
    CASE WHEN esperado = '0' THEN 0 ELSE 1 END,
    tabela;

COMMIT;

-- ─────────────────────────────────────────────────────────────
-- RESULTADO ESPERADO
-- Tabelas de clientes          → total = 0
-- auth.users / user_profiles   → apenas admins (≥ 1)
-- Tabelas administrativas      → intactas (valor original)
-- ─────────────────────────────────────────────────────────────
