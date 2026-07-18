-- Migration: 0042_trial_campaigns.sql
-- Substitui a configuração de Trial embutida em plan_version_modules (trial_enabled/
-- trial_days, adicionados em 0038) por uma entidade de primeira classe: Trial Campaign.
-- Um plan_version_modules (módulo dentro de um plano) pode ter várias campanhas de
-- Trial ao longo do tempo, cada uma com seu próprio limite de vagas, período de
-- validade e prioridade. A seleção da campanha vigente é feita em tempo de execução
-- (ver TrialCampaignService), não fixada aqui.

-- DOWN:
-- DROP TABLE IF EXISTS trial_campaigns;
-- ALTER TABLE plan_version_modules ADD COLUMN IF NOT EXISTS trial_enabled BOOLEAN NOT NULL DEFAULT FALSE;
-- ALTER TABLE plan_version_modules ADD COLUMN IF NOT EXISTS trial_days INTEGER;

ALTER TABLE plan_version_modules
    DROP CONSTRAINT IF EXISTS chk_pvm_trial_days,
    DROP COLUMN IF EXISTS trial_enabled,
    DROP COLUMN IF EXISTS trial_days;

CREATE TABLE trial_campaigns (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_version_module_id  UUID        NOT NULL REFERENCES plan_version_modules(id) ON DELETE CASCADE,
    name                    TEXT        NOT NULL,
    status                  TEXT        NOT NULL DEFAULT 'SCHEDULED',
    days                    INTEGER     NOT NULL,
    max_slots               INTEGER     NOT NULL,
    used_slots              INTEGER     NOT NULL DEFAULT 0,
    start_date              DATE,
    end_date                DATE,
    notes                   TEXT,
    priority                INTEGER     NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_tc_status     CHECK (status IN ('ACTIVE', 'SCHEDULED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_tc_days       CHECK (days BETWEEN 1 AND 365),
    CONSTRAINT chk_tc_max_slots  CHECK (max_slots >= 1),
    CONSTRAINT chk_tc_used_slots CHECK (used_slots >= 0 AND used_slots <= max_slots),
    CONSTRAINT chk_tc_dates      CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

-- Índice usado pela seleção da campanha vigente (status ativo, maior prioridade,
-- desempate pela mais antiga).
CREATE INDEX idx_trial_campaigns_selection
    ON trial_campaigns(plan_version_module_id, status, priority DESC, created_at ASC);

CREATE TRIGGER trg_trial_campaigns_updated_at
    BEFORE UPDATE ON trial_campaigns
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
