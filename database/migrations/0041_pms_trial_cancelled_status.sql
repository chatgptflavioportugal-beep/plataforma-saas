-- Migration: 0041_pms_trial_cancelled_status.sql
-- Adiciona o status TRIAL_CANCELLED: cancelar um Trial não revoga o acesso
-- imediatamente — apenas cancela a renovação automática. O acesso permanece
-- válido até expires_at (trial_end_at), quando o scheduler expira o módulo.

-- DOWN:
-- ALTER TABLE profile_module_subscriptions DROP CONSTRAINT IF EXISTS chk_pms_status;
-- ALTER TABLE profile_module_subscriptions ADD CONSTRAINT chk_pms_status
--     CHECK (status IN ('TRIAL', 'ACTIVE', 'PENDING_PAYMENT', 'CANCELED', 'EXPIRED'));

ALTER TABLE profile_module_subscriptions
    DROP CONSTRAINT IF EXISTS chk_pms_status;

ALTER TABLE profile_module_subscriptions
    ADD CONSTRAINT chk_pms_status
    CHECK (status IN ('TRIAL', 'TRIAL_CANCELLED', 'ACTIVE', 'PENDING_PAYMENT', 'CANCELED', 'EXPIRED'));
