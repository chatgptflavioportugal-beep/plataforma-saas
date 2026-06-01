-- Migration: 0028_admin_access_levels.sql
-- Níveis de Acesso Administrativos da Plataforma
-- Separado dos níveis de acesso do perfil empresarial (profile_access_levels)

-- DOWN (reverter na ordem inversa):
-- ALTER TABLE user_profiles DROP COLUMN IF EXISTS admin_access_level_id;
-- DROP TABLE IF EXISTS admin_access_level_permissions;
-- DROP TABLE IF EXISTS admin_access_levels;
-- ALTER TABLE user_profiles DROP CONSTRAINT IF EXISTS chk_user_profiles_system_role;
-- ALTER TABLE user_profiles ADD CONSTRAINT chk_user_profiles_system_role CHECK (system_role IN ('user', 'SUPER_ADMIN'));

-- ─── 1. Atualizar constraint de system_role ────────────────────────────────────
-- Adiciona ADMIN_USER como papel possível para usuários administrativos da plataforma
ALTER TABLE user_profiles DROP CONSTRAINT IF EXISTS chk_user_profiles_system_role;
ALTER TABLE user_profiles
    ADD CONSTRAINT chk_user_profiles_system_role
    CHECK (system_role IN ('user', 'SUPER_ADMIN', 'ADMIN_USER'));

-- ─── 2. Tabela de Níveis de Acesso Administrativos ────────────────────────────
CREATE TABLE admin_access_levels (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    description TEXT,
    status      TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_admin_access_level_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_admin_access_levels_status ON admin_access_levels(status);

CREATE TRIGGER update_admin_access_levels_updated_at
    BEFORE UPDATE ON admin_access_levels
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ─── 3. Tabela de Permissões por Nível de Acesso Admin ───────────────────────
-- Usa chaves fixas (permission_key) que representam ações na área administrativa
-- Exemplo: admin.plans.create, admin.subscriptions.cancel
CREATE TABLE admin_access_level_permissions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    access_level_id UUID        NOT NULL REFERENCES admin_access_levels(id) ON DELETE CASCADE,
    permission_key  TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_admin_alp UNIQUE (access_level_id, permission_key)
);

CREATE INDEX idx_admin_alp_level ON admin_access_level_permissions(access_level_id);

-- ─── 4. Vincular admin_access_level_id em user_profiles ──────────────────────
-- Usuários com system_role = 'ADMIN_USER' possuem um nível de acesso administrativo
ALTER TABLE user_profiles
    ADD COLUMN admin_access_level_id UUID REFERENCES admin_access_levels(id) ON DELETE SET NULL;
