-- Migration: 0019_plan_version_modules.sql
-- Vincula módulos a versões de plano com preços individuais por módulo

-- DOWN:
-- DROP TABLE IF EXISTS plan_version_modules;

CREATE TABLE plan_version_modules (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id       UUID          NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    module_id     UUID          NOT NULL REFERENCES platform_modules(id),
    monthly_price DECIMAL(10,2) NOT NULL DEFAULT 0,
    annual_price  DECIMAL(10,2) NOT NULL DEFAULT 0,
    status        TEXT          NOT NULL DEFAULT 'active'
        CONSTRAINT chk_pvm_status CHECK (status IN ('active', 'inactive')),
    sort_order    INTEGER       NOT NULL DEFAULT 99,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_plan_version_module UNIQUE (plan_id, module_id)
);

CREATE TRIGGER trg_plan_version_modules_updated_at
    BEFORE UPDATE ON plan_version_modules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_pvm_plan_id   ON plan_version_modules(plan_id);
CREATE INDEX idx_pvm_module_id ON plan_version_modules(module_id);
CREATE INDEX idx_pvm_status    ON plan_version_modules(status);
