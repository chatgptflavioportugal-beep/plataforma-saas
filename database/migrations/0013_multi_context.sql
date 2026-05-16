-- Migration: 0013_multi_context.sql
-- Suporte a múltiplos contextos: tenant individual, tipos de plano, convites, papéis expandidos

-- DOWN (reverter na ordem inversa):
-- DROP TABLE IF EXISTS invitations;
-- ALTER TABLE user_tenants DROP CONSTRAINT chk_user_tenants_role;
-- ALTER TABLE user_tenants ADD CONSTRAINT chk_user_tenants_role CHECK (role IN ('owner', 'admin', 'member'));
-- ALTER TABLE user_profiles DROP CONSTRAINT chk_user_profiles_system_role;
-- ALTER TABLE user_profiles ADD CONSTRAINT chk_user_profiles_system_role CHECK (system_role IN ('user', 'SUPER_ADMIN'));
-- ALTER TABLE plans DROP COLUMN plan_type;
-- ALTER TABLE tenants DROP COLUMN type;

-- ─── 1. Tipo de tenant ──────────────────────────────────────────────────────
ALTER TABLE tenants ADD COLUMN type TEXT NOT NULL DEFAULT 'business';
ALTER TABLE tenants ADD CONSTRAINT chk_tenants_type
    CHECK (type IN ('individual', 'business'));

-- ─── 2. Tipo de plano ───────────────────────────────────────────────────────
ALTER TABLE plans ADD COLUMN plan_type TEXT NOT NULL DEFAULT 'business';
ALTER TABLE plans ADD CONSTRAINT chk_plans_plan_type
    CHECK (plan_type IN ('individual', 'business'));

-- ─── 3. Papel 'finance' dentro do tenant ────────────────────────────────────
ALTER TABLE user_tenants DROP CONSTRAINT chk_user_tenants_role;
ALTER TABLE user_tenants ADD CONSTRAINT chk_user_tenants_role
    CHECK (role IN ('owner', 'admin', 'member', 'finance'));

-- ─── 4. Papéis globais expandidos ──────────────────────────────────────────
ALTER TABLE user_profiles DROP CONSTRAINT chk_user_profiles_system_role;
ALTER TABLE user_profiles ADD CONSTRAINT chk_user_profiles_system_role
    CHECK (system_role IN ('user', 'SUPER_ADMIN', 'ADMIN', 'SUPPORT', 'FINANCE_ADMIN'));

-- ─── 5. Tabela de convites ──────────────────────────────────────────────────
CREATE TABLE invitations (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invited_by  UUID        NOT NULL REFERENCES auth.users(id),
    email       TEXT        NOT NULL,
    role        TEXT        NOT NULL DEFAULT 'member',
    token       TEXT        NOT NULL UNIQUE DEFAULT encode(gen_random_bytes(32), 'hex'),
    status      TEXT        NOT NULL DEFAULT 'pending',
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '7 days'),
    accepted_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_invitations_role   CHECK (role   IN ('admin', 'member', 'finance')),
    CONSTRAINT chk_invitations_status CHECK (status IN ('pending', 'accepted', 'expired', 'cancelled'))
);

CREATE INDEX idx_invitations_tenant_id ON invitations(tenant_id);
CREATE INDEX idx_invitations_token     ON invitations(token);
CREATE INDEX idx_invitations_email     ON invitations(email);
CREATE INDEX idx_invitations_status    ON invitations(status);

ALTER TABLE invitations ENABLE ROW LEVEL SECURITY;

-- Apenas owners e admins do tenant podem ver os convites dele
CREATE POLICY invitations_admin_select ON invitations
FOR SELECT USING (
    tenant_id IN (
        SELECT tenant_id FROM user_tenants
        WHERE user_id = auth.uid() AND is_active = TRUE AND role IN ('owner', 'admin')
    )
);

-- ─── 6. Trigger atualizado: cria tenant individual ao registrar ─────────────
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    v_tenant_id UUID;
    v_plan_id   UUID;
    v_name      TEXT;
    v_slug      TEXT;
BEGIN
    -- Criar perfil do usuário
    INSERT INTO public.user_profiles (id, full_name, avatar_url)
    VALUES (
        NEW.id,
        NEW.raw_user_meta_data ->> 'full_name',
        NEW.raw_user_meta_data ->> 'avatar_url'
    );

    -- Nome para o tenant individual (usa nome, e-mail ou fallback)
    v_name := COALESCE(
        NULLIF(TRIM(NEW.raw_user_meta_data ->> 'full_name'), ''),
        split_part(NEW.email, '@', 1),
        'Meu Plano'
    );

    -- Slug único baseado no UUID do usuário
    v_slug := 'individual-' || replace(NEW.id::text, '-', '');

    -- Criar tenant individual (sempre status trial)
    INSERT INTO public.tenants (name, slug, type, status, trial_ends_at)
    VALUES (v_name, v_slug, 'individual', 'trial', NOW() + INTERVAL '14 days')
    RETURNING id INTO v_tenant_id;

    -- Usuário é owner do próprio tenant individual
    INSERT INTO public.user_tenants (user_id, tenant_id, role)
    VALUES (NEW.id, v_tenant_id, 'owner');

    -- Criar subscription com o plano individual de menor sort_order disponível
    SELECT id INTO v_plan_id
    FROM public.plans
    WHERE plan_type = 'individual'
      AND is_active = TRUE
      AND is_current_version = TRUE
    ORDER BY sort_order
    LIMIT 1;

    IF v_plan_id IS NOT NULL THEN
        INSERT INTO public.tenant_subscriptions (tenant_id, plan_id, status, trial_start, trial_end)
        VALUES (v_tenant_id, v_plan_id, 'trial', NOW(), NOW() + INTERVAL '14 days');
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
