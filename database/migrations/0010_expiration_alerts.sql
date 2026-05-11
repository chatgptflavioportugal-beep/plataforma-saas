-- Migration: 0010_expiration_alerts.sql
-- Alertas de expiração de trial

-- DOWN:
-- DROP TABLE IF EXISTS expiration_alerts;

CREATE TABLE expiration_alerts (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    alert_type      TEXT        NOT NULL,
    days_remaining  INTEGER     NOT NULL,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_expiration_alerts_type
        CHECK (alert_type IN ('7_days', '3_days', '1_day', 'expired'))
);

CREATE INDEX idx_expiration_alerts_tenant_id ON expiration_alerts(tenant_id);
CREATE INDEX idx_expiration_alerts_sent_at ON expiration_alerts(sent_at DESC);
