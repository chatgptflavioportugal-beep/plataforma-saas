-- Migration: 0051_least_privilege_roles.sql
--
-- Isolamento de acesso a banco por serviço (least privilege), tornando
-- estrutural a regra de que somente o admin-service escreve dados
-- administrativos (Catálogo, Comercial, Administração, Gestão Global) e os
-- demais serviços só fazem SELECT sobre essas tabelas.
--
-- Hoje TODOS os serviços conectam com a mesma credencial (QUARKUS_DATASOURCE_
-- USERNAME/PASSWORD), sem nenhuma separação de role no Postgres — a regra é
-- garantida só por convenção de código. Este script é o ponto de partida
-- para corrigir isso, mas é entregue como SCAFFOLDING, não como corte
-- imediato de produção:
--   1. Requer acesso ao projeto Supabase (SQL Editor/CLI) para ser aplicado —
--      não há Postgres local neste docker-compose.
--   2. Depois de aplicado, é preciso gerar/definir uma senha para cada ROLE
--      abaixo (`ALTER ROLE role_xxx WITH PASSWORD '...'`) e só então apontar
--      cada serviço para sua própria credencial (ver comentário no fim).
--   3. Até que os passos 1-2 sejam feitos manualmente, nada muda — todos os
--      serviços continuam usando a credencial atual compartilhada.
-- Revise o mapeamento de tabelas por serviço abaixo antes de aplicar em
-- produção; foi construído a partir do código atual, mas pode haver tabelas
-- adicionais que um serviço específico ainda não tenha sido auditado a tocar.

-- DOWN:
-- REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM
--   role_admin_service, role_subscription_service, role_module_catalog_service,
--   role_profile_service, role_usage_service, role_auth_service,
--   role_pdf_service, role_whatsapp_service;
-- DROP ROLE IF EXISTS role_admin_service;
-- DROP ROLE IF EXISTS role_subscription_service;
-- DROP ROLE IF EXISTS role_module_catalog_service;
-- DROP ROLE IF EXISTS role_profile_service;
-- DROP ROLE IF EXISTS role_usage_service;
-- DROP ROLE IF EXISTS role_auth_service;
-- DROP ROLE IF EXISTS role_pdf_service;
-- DROP ROLE IF EXISTS role_whatsapp_service;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'role_admin_service') THEN
        CREATE ROLE role_admin_service LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'role_subscription_service') THEN
        CREATE ROLE role_subscription_service LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'role_module_catalog_service') THEN
        CREATE ROLE role_module_catalog_service LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'role_profile_service') THEN
        CREATE ROLE role_profile_service LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'role_usage_service') THEN
        CREATE ROLE role_usage_service LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'role_auth_service') THEN
        CREATE ROLE role_auth_service LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'role_pdf_service') THEN
        CREATE ROLE role_pdf_service LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'role_whatsapp_service') THEN
        CREATE ROLE role_whatsapp_service LOGIN;
    END IF;
END $$;

-- ─── admin-service — único dono dos dados estruturais da plataforma ─────────
-- Catálogo, Comercial, Administração e Gestão Global (INSERT/UPDATE/DELETE).
GRANT SELECT, INSERT, UPDATE, DELETE ON
    plans, plan_version_modules, plan_version_module_limits,
    platform_modules, platform_module_services, platform_module_service_groups,
    trial_campaigns, platform_settings, feature_flags,
    admin_access_levels, admin_access_level_permissions,
    tenants, user_profiles
    TO role_admin_service;
GRANT SELECT, INSERT ON admin_audit_logs TO role_admin_service;
-- Leitura ampla necessária para os relatórios/telas administrativas (stats,
-- listagem de assinaturas, participantes de trial etc.)
GRANT SELECT ON ALL TABLES IN SCHEMA public TO role_admin_service;

-- ─── subscription-service — ciclo de vida de assinaturas ───────────────────
-- Escreve apenas nas tabelas que possui (assinaturas, histórico de trial,
-- auditoria própria); todo o resto do catálogo/comercial é SELECT-only.
GRANT SELECT, INSERT, UPDATE, DELETE ON
    profile_module_subscriptions, module_trial_history, audit_logs
    TO role_subscription_service;
GRANT SELECT ON
    plans, plan_version_modules, plan_version_module_limits,
    platform_modules, platform_module_services, platform_module_service_groups,
    trial_campaigns, platform_settings,
    tenants, user_profiles, user_tenants, tenant_subscriptions
    TO role_subscription_service;

-- ─── module-catalog-service — somente leitura do catálogo ──────────────────
GRANT SELECT ON
    platform_modules, platform_module_services, platform_module_service_groups
    TO role_module_catalog_service;

-- ─── profile-service — estrutura operacional do cliente ────────────────────
-- Empresas do cliente (INSERT no onboarding), membros, convites, níveis de
-- acesso e preferências pertencem a este serviço.
GRANT SELECT, INSERT, UPDATE, DELETE ON
    tenants, user_tenants, invitations,
    profile_access_levels, profile_access_level_permissions,
    profile_access_level_admin_permissions
    TO role_profile_service;
GRANT SELECT ON
    platform_modules, platform_module_services, user_profiles
    TO role_profile_service;

-- ─── usage-service — consumo/quotas ─────────────────────────────────────────
GRANT SELECT, INSERT, UPDATE, DELETE ON
    module_usage_counters, usage_audit_logs
    TO role_usage_service;

-- ─── auth-service — apenas autenticação/tokens, sem escrita de negócio ──────
GRANT SELECT ON
    user_profiles, tenants, user_tenants, tenant_subscriptions,
    platform_modules, platform_module_services, profile_access_level_permissions
    TO role_auth_service;

-- ─── pdf-service / whatsapp-service — apenas o próprio módulo ──────────────
GRANT SELECT, INSERT, UPDATE, DELETE ON pdf_jobs TO role_pdf_service;
-- whatsapp-service ainda não possui tabela própria (endpoint stub); nenhum
-- GRANT de escrita até que uma exista.

-- ─────────────────────────────────────────────────────────────────────────
-- Próximos passos manuais (fora do alcance desta migração):
--   ALTER ROLE role_<servico> WITH PASSWORD '<senha forte gerada>';
--   -- depois, no .env/docker-compose de cada serviço, apontar
--   -- QUARKUS_DATASOURCE_USERNAME/PASSWORD (ou DATABASE_URL, no caso de
--   -- pdf-service/whatsapp-service em Python) para a role correspondente.
-- Os application.properties de admin-service/subscription-service/
-- module-catalog-service/profile-service/usage-service/auth-service já
-- aceitam essa troca via variáveis <SERVICO>_DB_USERNAME/<SERVICO>_DB_PASSWORD
-- com fallback para as credenciais compartilhadas atuais — nada quebra até
-- que essas variáveis sejam explicitamente definidas.
