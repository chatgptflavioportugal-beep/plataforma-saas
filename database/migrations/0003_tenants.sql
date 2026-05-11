-- Migration: 0003_tenants.sql
-- Tabela de tenants (empresas/organizações)

-- DOWN:
-- DROP TABLE IF EXISTS tenants;

CREATE TABLE tenants (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT        NOT NULL,
    slug            TEXT        UNIQUE NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'trial',
    plan_id         UUID        REFERENCES plans(id),
    trial_ends_at   TIMESTAMPTZ,
    settings        JSONB       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_tenants_status CHECK (status IN ('trial', 'active', 'suspended', 'cancelled'))
);

CREATE TRIGGER trg_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_tenants_slug ON tenants(slug);
CREATE INDEX idx_tenants_status ON tenants(status);
CREATE INDEX idx_tenants_plan_id ON tenants(plan_id);
