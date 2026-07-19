-- Migration: 0046_trial_campaigns_audit_fields.sql
-- Rastreabilidade administrativa das Trial Campaigns: quem criou e quem foi o
-- último a alterar (inclui cancelamento). Sem FK — mesmo padrão já usado em
-- module_trial_history.started_by_user_id, resolvido ad-hoc via join com
-- user_profiles/auth.users quando necessário exibir nome.

-- DOWN:
-- ALTER TABLE trial_campaigns DROP COLUMN IF EXISTS created_by_user_id;
-- ALTER TABLE trial_campaigns DROP COLUMN IF EXISTS updated_by_user_id;

ALTER TABLE trial_campaigns
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS updated_by_user_id UUID;
