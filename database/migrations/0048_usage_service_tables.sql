-- Migration: 0048_usage_service_tables.sql
-- Tabelas do novo Usage Service: contadores de uso diário por tenant/módulo/métrica
-- (para quota/rate-limit) e auditoria de uso de módulo, substituindo o audit_logs
-- genérico do backend-quarkus (que só era usado pelo módulo PDF removido nesta etapa).

-- DOWN:
-- DROP TABLE IF EXISTS usage_audit_logs;
-- DROP TABLE IF EXISTS module_usage_counters;

CREATE TABLE IF NOT EXISTS module_usage_counters (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    module_slug TEXT NOT NULL,
    metric_code TEXT NOT NULL,
    period_date DATE NOT NULL,
    count       BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, module_slug, metric_code, period_date)
);

CREATE INDEX IF NOT EXISTS idx_module_usage_counters_lookup
    ON module_usage_counters (tenant_id, module_slug, metric_code, period_date);

CREATE TABLE IF NOT EXISTS usage_audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    user_id     UUID,
    module_slug TEXT NOT NULL,
    metric_code TEXT,
    action      TEXT NOT NULL,
    metadata    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_usage_audit_logs_tenant_module
    ON usage_audit_logs (tenant_id, module_slug, created_at DESC);
