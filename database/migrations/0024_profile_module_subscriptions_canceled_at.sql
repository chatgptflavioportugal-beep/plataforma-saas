-- Migration: 0024_profile_module_subscriptions_canceled_at.sql
-- Adiciona campo canceled_at e expande status para incluir EXPIRED

-- DOWN:
-- ALTER TABLE profile_module_subscriptions DROP COLUMN IF EXISTS canceled_at;
-- ALTER TABLE profile_module_subscriptions DROP CONSTRAINT IF EXISTS chk_pms_status;
-- ALTER TABLE profile_module_subscriptions ADD CONSTRAINT chk_pms_status
--     CHECK (status IN ('ACTIVE', 'PENDING_PAYMENT', 'CANCELED'));

ALTER TABLE profile_module_subscriptions
    ADD COLUMN IF NOT EXISTS canceled_at TIMESTAMPTZ;

-- Expande o constraint de status para incluir EXPIRED
ALTER TABLE profile_module_subscriptions
    DROP CONSTRAINT IF EXISTS chk_pms_status;

ALTER TABLE profile_module_subscriptions
    ADD CONSTRAINT chk_pms_status
    CHECK (status IN ('ACTIVE', 'PENDING_PAYMENT', 'CANCELED', 'EXPIRED'));
