-- Migration: 0020_plan_version_module_limits.sql
-- Limitações estruturadas por módulo dentro de uma versão de plano

-- DOWN:
-- DROP TABLE IF EXISTS plan_version_module_limits;

CREATE TABLE plan_version_module_limits (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_version_module_id UUID        NOT NULL REFERENCES plan_version_modules(id) ON DELETE CASCADE,
    title                  TEXT        NOT NULL,
    description            TEXT,
    limit_key              TEXT,
    limit_value            TEXT,
    unit                   TEXT,
    sort_order             INTEGER     NOT NULL DEFAULT 99,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_plan_version_module_limits_updated_at
    BEFORE UPDATE ON plan_version_module_limits
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_pvml_pvm_id     ON plan_version_module_limits(plan_version_module_id);
CREATE INDEX idx_pvml_sort_order ON plan_version_module_limits(sort_order);
