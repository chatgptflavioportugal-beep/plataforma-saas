-- Migration: 0023_profile_module_subscriptions.sql
-- Assinaturas de módulos vinculadas ao perfil (tenant) ativo do usuário.
-- Cada perfil (individual ou empresarial) possui seus próprios módulos contratados,
-- isolados de outros perfis do mesmo usuário.

-- Limpa caso a migration tenha rodado parcialmente em tentativa anterior
DROP TABLE IF EXISTS profile_module_subscriptions CASCADE;

CREATE TABLE profile_module_subscriptions (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    module_id           UUID          NOT NULL REFERENCES platform_modules(id),
    plan_version_id     UUID          NOT NULL REFERENCES plan_version_modules(id),
    billing_cycle       TEXT          NOT NULL
        CONSTRAINT chk_pms_billing_cycle CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL')),
    status              TEXT          NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT chk_pms_status CHECK (status IN ('ACTIVE', 'PENDING_PAYMENT', 'CANCELED')),
    started_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ,
    created_by_user_id  UUID          NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    -- Um perfil só pode ter uma assinatura ativa por módulo
    CONSTRAINT uq_profile_module UNIQUE (tenant_id, module_id)
);

CREATE TRIGGER trg_profile_module_subscriptions_updated_at
    BEFORE UPDATE ON profile_module_subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX IF NOT EXISTS idx_pms_tenant_id ON profile_module_subscriptions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pms_module_id ON profile_module_subscriptions(module_id);
CREATE INDEX IF NOT EXISTS idx_pms_status    ON profile_module_subscriptions(status);
