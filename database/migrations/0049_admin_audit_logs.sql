-- Migration: 0049_admin_audit_logs.sql
-- Auditoria administrativa dedicada ao novo Admin Service: registra quem fez
-- qual mutação (tenants, usuários admin, níveis de acesso, assinaturas). Não
-- reutiliza o audit_logs antigo do backend-quarkus (que era genérico e ligado
-- ao módulo PDF removido nesta etapa).

-- DOWN:
-- DROP TABLE IF EXISTS admin_audit_logs;

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID NOT NULL,
    action        TEXT NOT NULL,
    resource      TEXT,
    resource_id   TEXT,
    metadata      JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_actor
    ON admin_audit_logs (actor_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_resource
    ON admin_audit_logs (resource, resource_id);
