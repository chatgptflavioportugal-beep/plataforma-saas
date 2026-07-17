-- Migration: 0036_pms_billing_cycle_free.sql
-- Amplia o billing_cycle de profile_module_subscriptions para aceitar 'FREE',
-- usado pela ativação sob demanda (lazy activation) de módulos com plano Free.

-- DOWN:
-- ALTER TABLE profile_module_subscriptions DROP CONSTRAINT IF EXISTS chk_pms_billing_cycle;
-- ALTER TABLE profile_module_subscriptions ADD CONSTRAINT chk_pms_billing_cycle
--     CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL'));

ALTER TABLE profile_module_subscriptions
    DROP CONSTRAINT IF EXISTS chk_pms_billing_cycle;

ALTER TABLE profile_module_subscriptions
    ADD CONSTRAINT chk_pms_billing_cycle
    CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL', 'FREE'));
