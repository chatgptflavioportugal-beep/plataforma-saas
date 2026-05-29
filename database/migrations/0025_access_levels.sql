-- Migration: 0025_access_levels.sql
-- Níveis de Acesso personalizados por Perfil Empresarial
-- Substitui o conceito de "Papel" nos convites e membros

-- DOWN (reverter na ordem inversa):
-- DROP TABLE IF EXISTS profile_access_level_permissions;
-- DROP TABLE IF EXISTS profile_access_levels;
-- ALTER TABLE user_tenants DROP COLUMN IF EXISTS access_level_id;
-- ALTER TABLE invitations DROP COLUMN IF EXISTS access_level_id;

-- ─── 1. Tabela de Níveis de Acesso ──────────────────────────────────────────
CREATE TABLE profile_access_levels (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    description TEXT,
    status      TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_access_level_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_access_levels_tenant ON profile_access_levels(tenant_id);

ALTER TABLE profile_access_levels ENABLE ROW LEVEL SECURITY;

-- Qualquer membro do tenant pode visualizar os níveis (para exibição na UI)
CREATE POLICY pal_member_select ON profile_access_levels
FOR SELECT USING (
    tenant_id IN (
        SELECT tenant_id FROM user_tenants
        WHERE user_id = auth.uid() AND is_active = TRUE
    )
);

-- ─── 2. Tabela de Permissões por Nível de Acesso ─────────────────────────────
-- Permissões são sempre no nível de serviço (service_id NOT NULL)
-- O checkbox de módulo na árvore é apenas conveniência de UI
CREATE TABLE profile_access_level_permissions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    access_level_id UUID        NOT NULL REFERENCES profile_access_levels(id) ON DELETE CASCADE,
    module_id       UUID        NOT NULL REFERENCES platform_modules(id) ON DELETE CASCADE,
    service_id      UUID        NOT NULL REFERENCES platform_module_services(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_palp UNIQUE (access_level_id, service_id)
);

CREATE INDEX idx_palp_level ON profile_access_level_permissions(access_level_id);

ALTER TABLE profile_access_level_permissions ENABLE ROW LEVEL SECURITY;

CREATE POLICY palp_member_select ON profile_access_level_permissions
FOR SELECT USING (
    access_level_id IN (
        SELECT pal.id FROM profile_access_levels pal
        WHERE pal.tenant_id IN (
            SELECT tenant_id FROM user_tenants
            WHERE user_id = auth.uid() AND is_active = TRUE
        )
    )
);

-- ─── 3. Vínculo de access_level_id em user_tenants ────────────────────────────
ALTER TABLE user_tenants
    ADD COLUMN access_level_id UUID REFERENCES profile_access_levels(id) ON DELETE SET NULL;

-- ─── 4. Vínculo de access_level_id em invitations ────────────────────────────
ALTER TABLE invitations
    ADD COLUMN access_level_id UUID REFERENCES profile_access_levels(id) ON DELETE SET NULL;

-- ─── 5. Atualiza accept_invitation para propagar access_level_id ──────────────
CREATE OR REPLACE FUNCTION accept_invitation(p_token TEXT, p_user_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_inv            invitations%ROWTYPE;
    v_already_member BOOLEAN;
BEGIN
    SELECT * INTO v_inv
    FROM invitations
    WHERE token = p_token
      AND status = 'pending'
      AND expires_at > NOW();

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'Convite inválido ou expirado');
    END IF;

    SELECT EXISTS(
        SELECT 1 FROM user_tenants
        WHERE user_id = p_user_id
          AND tenant_id = v_inv.tenant_id
          AND is_active = TRUE
    ) INTO v_already_member;

    IF v_already_member THEN
        UPDATE invitations SET status = 'accepted', accepted_at = NOW() WHERE id = v_inv.id;
        RETURN jsonb_build_object(
            'success', true,
            'tenant_id', v_inv.tenant_id,
            'role', v_inv.role,
            'already_member', true
        );
    END IF;

    INSERT INTO user_tenants (user_id, tenant_id, role, access_level_id)
    VALUES (p_user_id, v_inv.tenant_id, v_inv.role, v_inv.access_level_id)
    ON CONFLICT (user_id, tenant_id)
    DO UPDATE SET
        role             = EXCLUDED.role,
        access_level_id  = EXCLUDED.access_level_id,
        is_active        = TRUE,
        updated_at       = NOW();

    UPDATE invitations
    SET status = 'accepted', accepted_at = NOW()
    WHERE id = v_inv.id;

    RETURN jsonb_build_object(
        'success', true,
        'tenant_id', v_inv.tenant_id,
        'role', v_inv.role,
        'already_member', false
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
