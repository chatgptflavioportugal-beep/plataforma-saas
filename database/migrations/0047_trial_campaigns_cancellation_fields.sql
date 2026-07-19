-- Migration: 0047_trial_campaigns_cancellation_fields.sql
-- Registra quando e por que uma trial_campaign foi cancelada (manual ou automaticamente
-- ao gerar uma nova versão do plano). Sem isso, o cancelamento só ficava visível pelo
-- status + audit_logs, sem um motivo legível diretamente na campanha.

-- DOWN:
-- ALTER TABLE trial_campaigns DROP COLUMN IF EXISTS cancelled_at;
-- ALTER TABLE trial_campaigns DROP COLUMN IF EXISTS cancel_reason;

ALTER TABLE trial_campaigns
    ADD COLUMN IF NOT EXISTS cancelled_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancel_reason TEXT;
