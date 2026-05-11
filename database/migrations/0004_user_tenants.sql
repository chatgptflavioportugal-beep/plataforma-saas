-- Migration: 0004_user_tenants.sql
-- Relação usuário-tenant com papéis

-- DOWN:
-- DROP TABLE IF EXISTS user_tenants;

CREATE TABLE user_tenants (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    tenant_id   UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    role        TEXT        NOT NULL DEFAULT 'member',
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_user_tenants_role CHECK (role IN ('owner', 'admin', 'member')),
    CONSTRAINT uq_user_tenants UNIQUE (user_id, tenant_id)
);

CREATE TRIGGER trg_user_tenants_updated_at
    BEFORE UPDATE ON user_tenants
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_user_tenants_user_id ON user_tenants(user_id);
CREATE INDEX idx_user_tenants_tenant_id ON user_tenants(tenant_id);
CREATE INDEX idx_user_tenants_active ON user_tenants(user_id, tenant_id) WHERE is_active = TRUE;

-- RLS
ALTER TABLE user_tenants ENABLE ROW LEVEL SECURITY;

CREATE POLICY user_tenants_tenant_select ON user_tenants
FOR SELECT USING (
    user_id = auth.uid()
    OR tenant_id IN (
        SELECT tenant_id FROM user_tenants
        WHERE user_id = auth.uid() AND is_active = TRUE AND role IN ('owner', 'admin')
    )
);
