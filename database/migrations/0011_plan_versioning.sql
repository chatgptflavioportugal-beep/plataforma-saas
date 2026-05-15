-- Migration: 0011_plan_versioning.sql
-- Adiciona versionamento de planos, preços anuais e campos de assinatura contratados

-- ============================================================
-- 1. Preparar tabela plans para versionamento
-- ============================================================

-- Remove a restrição UNIQUE em code (múltiplas versões podem ter o mesmo code)
ALTER TABLE plans DROP CONSTRAINT IF EXISTS plans_code_key;

-- Remove o CHECK constraint fixo de valores para permitir novos planos futuros
ALTER TABLE plans DROP CONSTRAINT IF EXISTS chk_plans_code;

-- Adiciona colunas de versionamento e preço anual
ALTER TABLE plans
    ADD COLUMN IF NOT EXISTS price_annual           DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS discount_annual_percent DECIMAL(5,2)  NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version                INTEGER       NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS is_current_version     BOOLEAN       NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS parent_plan_id         UUID          REFERENCES plans(id),
    ADD COLUMN IF NOT EXISTS billing_type           TEXT          NOT NULL DEFAULT 'both'
        CONSTRAINT chk_plans_billing_type CHECK (billing_type IN ('monthly', 'annual', 'both'));

-- Garante unicidade por (code, version)
ALTER TABLE plans
    ADD CONSTRAINT plans_code_version_key UNIQUE (code, version);

-- Índice parcial para busca rápida da versão atual
CREATE INDEX IF NOT EXISTS idx_plans_current_version
    ON plans(code, is_current_version)
    WHERE is_current_version = TRUE;

-- ============================================================
-- 2. Atualiza os planos existentes com preço anual (20% desconto)
-- ============================================================

UPDATE plans SET
    price_annual = CASE
        WHEN price_monthly = 0 THEN 0.00
        ELSE ROUND(price_monthly * 0.80, 2)
    END,
    discount_annual_percent = CASE
        WHEN price_monthly = 0 THEN 0
        ELSE 20
    END,
    version = 1,
    is_current_version = TRUE
WHERE version = 1;

-- ============================================================
-- 3. Adiciona campos de assinatura contratada em tenant_subscriptions
-- ============================================================

ALTER TABLE tenant_subscriptions
    ADD COLUMN IF NOT EXISTS plan_version              INTEGER       NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS contracted_price_monthly  DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS contracted_price_annual   DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS billing_type              TEXT          NOT NULL DEFAULT 'monthly'
        CONSTRAINT chk_subscriptions_billing_type CHECK (billing_type IN ('monthly', 'annual'));

-- Retroativamente preenche contracted_price_monthly para assinaturas existentes
UPDATE tenant_subscriptions ts
SET
    plan_version = 1,
    contracted_price_monthly = p.price_monthly
FROM plans p
WHERE ts.plan_id = p.id
  AND ts.contracted_price_monthly IS NULL;

-- Índice para consultas de assinaturas por versão de plano
CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_plan_version
    ON tenant_subscriptions(plan_id, plan_version);

-- DOWN:
-- ALTER TABLE tenant_subscriptions
--     DROP COLUMN IF EXISTS plan_version,
--     DROP COLUMN IF EXISTS contracted_price_monthly,
--     DROP COLUMN IF EXISTS contracted_price_annual,
--     DROP COLUMN IF EXISTS billing_type;
--
-- ALTER TABLE plans
--     DROP COLUMN IF EXISTS price_annual,
--     DROP COLUMN IF EXISTS discount_annual_percent,
--     DROP COLUMN IF EXISTS version,
--     DROP COLUMN IF EXISTS is_current_version,
--     DROP COLUMN IF EXISTS parent_plan_id,
--     DROP COLUMN IF EXISTS billing_type;
-- ALTER TABLE plans ADD CONSTRAINT plans_code_key UNIQUE (code);
-- ALTER TABLE plans ADD CONSTRAINT chk_plans_code CHECK (code IN ('free', 'starter', 'pro', 'enterprise'));
