-- Migration: 0007_usage_records.sql
-- Registros de uso para controle de cotas

-- DOWN:
-- DROP TABLE IF EXISTS usage_records;

CREATE TABLE usage_records (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     UUID        REFERENCES auth.users(id) ON DELETE SET NULL,
    feature     TEXT        NOT NULL,
    quantity    INTEGER     NOT NULL DEFAULT 1,
    period      DATE        NOT NULL DEFAULT DATE_TRUNC('month', NOW()),
    metadata    JSONB       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usage_records_tenant_id ON usage_records(tenant_id);
CREATE INDEX idx_usage_records_feature_period ON usage_records(tenant_id, feature, period);

ALTER TABLE usage_records ENABLE ROW LEVEL SECURITY;

CREATE POLICY usage_records_tenant_select ON usage_records
FOR SELECT USING (
    tenant_id IN (
        SELECT tenant_id FROM user_tenants
        WHERE user_id = auth.uid() AND is_active = TRUE
    )
);
