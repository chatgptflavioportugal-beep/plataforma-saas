-- Migration: 0005_tenant_subscriptions.sql
-- Assinaturas dos tenants

-- DOWN:
-- DROP TABLE IF EXISTS tenant_subscriptions;

CREATE TABLE tenant_subscriptions (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan_id                 UUID        NOT NULL REFERENCES plans(id),
    status                  TEXT        NOT NULL DEFAULT 'trial',
    trial_start             TIMESTAMPTZ,
    trial_end               TIMESTAMPTZ,
    current_period_start    TIMESTAMPTZ,
    current_period_end      TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    metadata                JSONB       NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_tenant_subscriptions_status
        CHECK (status IN ('trial', 'active', 'past_due', 'cancelled', 'suspended'))
);

CREATE TRIGGER trg_tenant_subscriptions_updated_at
    BEFORE UPDATE ON tenant_subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_tenant_subscriptions_tenant_id ON tenant_subscriptions(tenant_id);
CREATE INDEX idx_tenant_subscriptions_status ON tenant_subscriptions(status);
CREATE INDEX idx_tenant_subscriptions_trial_end ON tenant_subscriptions(trial_end)
    WHERE status = 'trial';
