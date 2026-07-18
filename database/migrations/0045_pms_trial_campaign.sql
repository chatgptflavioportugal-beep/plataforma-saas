-- Migration: 0045_pms_trial_campaign.sql
-- profile_module_subscriptions passa a referenciar a trial_campaign usada
-- (rastreabilidade) e o registro correspondente em module_trial_history (para
-- cancelamento/reativação/scheduler atualizarem o histórico certo sem ambiguidade,
-- já que agora um mesmo tenant+módulo pode ter várias linhas de histórico ao
-- longo do tempo). trial_days/trial_start_at/trial_end_at/billing_starts_at
-- continuam congelados no momento da contratação (mesma filosofia já usada para
-- preços e limites do plano).

-- DOWN:
-- ALTER TABLE profile_module_subscriptions ADD COLUMN IF NOT EXISTS trial_enabled BOOLEAN NOT NULL DEFAULT FALSE;
-- ALTER TABLE profile_module_subscriptions DROP COLUMN IF EXISTS trial_campaign_id;
-- ALTER TABLE profile_module_subscriptions DROP COLUMN IF EXISTS trial_history_id;

ALTER TABLE profile_module_subscriptions
    DROP COLUMN IF EXISTS trial_enabled;

ALTER TABLE profile_module_subscriptions
    ADD COLUMN IF NOT EXISTS trial_campaign_id UUID REFERENCES trial_campaigns(id),
    ADD COLUMN IF NOT EXISTS trial_history_id  UUID REFERENCES module_trial_history(id);
