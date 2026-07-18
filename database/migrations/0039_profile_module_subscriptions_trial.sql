-- Migration: 0039_profile_module_subscriptions_trial.sql
-- Adiciona o estado TRIAL e os dados de trial congelados na assinatura do módulo.
-- Os campos trial_* são copiados de plan_version_modules no momento da contratação
-- e nunca mais recalculados a partir do plano — alterações futuras no plano não
-- afetam quem já iniciou um trial (mesmo comportamento já usado para preços/limites).

-- DOWN:
-- ALTER TABLE profile_module_subscriptions DROP COLUMN IF EXISTS trial_enabled;
-- ALTER TABLE profile_module_subscriptions DROP COLUMN IF EXISTS trial_days;
-- ALTER TABLE profile_module_subscriptions DROP COLUMN IF EXISTS trial_start_at;
-- ALTER TABLE profile_module_subscriptions DROP COLUMN IF EXISTS trial_end_at;
-- ALTER TABLE profile_module_subscriptions DROP COLUMN IF EXISTS billing_starts_at;
-- ALTER TABLE profile_module_subscriptions DROP CONSTRAINT IF EXISTS chk_pms_status;
-- ALTER TABLE profile_module_subscriptions ADD CONSTRAINT chk_pms_status
--     CHECK (status IN ('ACTIVE', 'PENDING_PAYMENT', 'CANCELED', 'EXPIRED'));

ALTER TABLE profile_module_subscriptions
    DROP CONSTRAINT IF EXISTS chk_pms_status;

ALTER TABLE profile_module_subscriptions
    ADD CONSTRAINT chk_pms_status
    CHECK (status IN ('TRIAL', 'ACTIVE', 'PENDING_PAYMENT', 'CANCELED', 'EXPIRED'));

ALTER TABLE profile_module_subscriptions
    ADD COLUMN IF NOT EXISTS trial_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS trial_days         INTEGER,
    ADD COLUMN IF NOT EXISTS trial_start_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS trial_end_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS billing_starts_at  TIMESTAMPTZ;
