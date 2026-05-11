-- Migration: 0008_user_profiles.sql
-- Perfis de usuário estendidos (além do auth.users)

-- DOWN:
-- DROP TABLE IF EXISTS user_profiles;

CREATE TABLE user_profiles (
    id              UUID        PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name       TEXT,
    avatar_url      TEXT,
    phone           TEXT,
    system_role     TEXT        NOT NULL DEFAULT 'user',
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    metadata        JSONB       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_user_profiles_system_role
        CHECK (system_role IN ('user', 'SUPER_ADMIN'))
);

CREATE TRIGGER trg_user_profiles_updated_at
    BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_user_profiles_system_role ON user_profiles(system_role);

ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY user_profiles_self_select ON user_profiles
FOR SELECT USING (id = auth.uid());

CREATE POLICY user_profiles_self_update ON user_profiles
FOR UPDATE USING (id = auth.uid());

-- Trigger para criar perfil automaticamente ao criar usuário
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.user_profiles (id, full_name, avatar_url)
    VALUES (
        NEW.id,
        NEW.raw_user_meta_data ->> 'full_name',
        NEW.raw_user_meta_data ->> 'avatar_url'
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE TRIGGER trg_auth_users_on_create
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION handle_new_user();
