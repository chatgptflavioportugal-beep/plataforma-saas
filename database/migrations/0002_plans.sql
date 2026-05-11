-- Migration: 0002_plans.sql
-- Tabela de planos disponíveis no SaaS

-- DOWN:
-- DROP TABLE IF EXISTS plans;

CREATE TABLE plans (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    TEXT        NOT NULL,
    code                    TEXT        UNIQUE NOT NULL,
    description             TEXT,
    price_monthly           DECIMAL(10,2) NOT NULL DEFAULT 0,
    max_users               INTEGER     NOT NULL DEFAULT 3,
    max_ai_requests_month   INTEGER     NOT NULL DEFAULT 50,
    features                JSONB       NOT NULL DEFAULT '{}',
    is_active               BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order              INTEGER     NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_plans_code CHECK (code IN ('free', 'starter', 'pro', 'enterprise'))
);

CREATE TRIGGER trg_plans_updated_at
    BEFORE UPDATE ON plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_plans_code ON plans(code);
CREATE INDEX idx_plans_is_active ON plans(is_active);
