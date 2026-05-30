-- Migration: 0026_access_level_admin_permissions.sql
-- Permissões administrativas por Nível de Acesso
-- Separadas das permissões de módulo/serviço (profile_access_level_permissions)

-- DOWN (reverter):
-- DROP TABLE IF EXISTS profile_access_level_admin_permissions;

-- ─── Tabela de Permissões Administrativas por Nível de Acesso ──────────────────
-- Armazena chaves fixas como "members.invite", "access_levels.edit", etc.
-- Não referencia módulos ou serviços — são ações administrativas do perfil.
CREATE TABLE profile_access_level_admin_permissions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    access_level_id UUID        NOT NULL REFERENCES profile_access_levels(id) ON DELETE CASCADE,
    permission_key  TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_palaap UNIQUE (access_level_id, permission_key),
    CONSTRAINT chk_palaap_key CHECK (permission_key ~ '^[a-z_]+\.[a-z_.]+$')
);

CREATE INDEX idx_palaap_level ON profile_access_level_admin_permissions(access_level_id);

ALTER TABLE profile_access_level_admin_permissions ENABLE ROW LEVEL SECURITY;

-- Qualquer membro do tenant pode visualizar as permissões do seu nível de acesso
CREATE POLICY palaap_member_select ON profile_access_level_admin_permissions
FOR SELECT USING (
    access_level_id IN (
        SELECT pal.id FROM profile_access_levels pal
        WHERE pal.tenant_id IN (
            SELECT tenant_id FROM user_tenants
            WHERE user_id = auth.uid() AND is_active = TRUE
        )
    )
);
