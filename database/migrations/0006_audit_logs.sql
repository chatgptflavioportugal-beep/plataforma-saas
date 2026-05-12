-- Migration: 0006_audit_logs.sql
-- Logs de auditoria de segurança

-- DOWN:
-- DROP TABLE IF EXISTS audit_logs;

CREATE TABLE audit_logs (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        REFERENCES tenants(id) ON DELETE SET NULL,
    user_id     UUID        REFERENCES auth.users(id) ON DELETE SET NULL,
    action      TEXT        NOT NULL,
    resource    TEXT,
    resource_id TEXT,
    metadata    JSONB       NOT NULL DEFAULT '{}',
    ip_address  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_tenant_id ON audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);

ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY audit_logs_tenant_select ON audit_logs
FOR SELECT USING (
    tenant_id IN (
        SELECT tenant_id FROM user_tenants
        WHERE user_id = auth.uid() AND is_active = TRUE AND role IN ('owner', 'admin')
    )
);
