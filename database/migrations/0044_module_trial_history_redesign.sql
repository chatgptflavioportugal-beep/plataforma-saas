-- Migration: 0044_module_trial_history_redesign.sql
-- Redesenha module_trial_history: deixava de ser "1 registro por tenant+módulo,
-- para sempre" (cap vitalício, criado em 0040) e passa a ser um ledger append-only,
-- usado tanto para a checagem de cooldown global (platform_settings) quanto para o
-- relatório de Participantes de cada Trial Campaign.

-- DOWN:
-- (não reversível com segurança sem perda de dados de rastreabilidade)

ALTER TABLE module_trial_history
    DROP CONSTRAINT IF EXISTS uq_module_trial_history;

ALTER TABLE module_trial_history
    RENAME COLUMN first_plan_version_id TO plan_version_module_id;

ALTER TABLE module_trial_history
    ADD COLUMN IF NOT EXISTS trial_campaign_id  UUID REFERENCES trial_campaigns(id),
    ADD COLUMN IF NOT EXISTS started_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS became_customer     BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE module_trial_history
    DROP COLUMN IF EXISTS trial_consumed;

CREATE INDEX IF NOT EXISTS idx_module_trial_history_tenant_module_started
    ON module_trial_history(tenant_id, module_id, trial_started_at DESC);

CREATE INDEX IF NOT EXISTS idx_module_trial_history_campaign
    ON module_trial_history(trial_campaign_id);
