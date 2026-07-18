-- Migration: 0040_module_trial_history.sql
-- Registro permanente de consumo de Trial por módulo, por perfil (tenant).
-- O Trial passa a ser um benefício único por módulo, independente do plano:
-- uma vez que exista um registro aqui, o perfil nunca mais recebe Trial
-- naquele módulo, não importa qual plano/versão seja escolhido depois.
-- Este registro nunca é removido.

-- DOWN:
-- DROP TABLE IF EXISTS module_trial_history;

CREATE TABLE module_trial_history (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    module_id              UUID        NOT NULL REFERENCES platform_modules(id),
    first_plan_version_id  UUID        NOT NULL REFERENCES plan_version_modules(id),
    trial_started_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    trial_finished_at      TIMESTAMPTZ,
    trial_canceled_at      TIMESTAMPTZ,
    trial_consumed         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Um único registro de Trial por módulo, por perfil, para sempre.
    CONSTRAINT uq_module_trial_history UNIQUE (tenant_id, module_id)
);

CREATE INDEX idx_module_trial_history_tenant_id ON module_trial_history(tenant_id);
CREATE INDEX idx_module_trial_history_module_id ON module_trial_history(module_id);
