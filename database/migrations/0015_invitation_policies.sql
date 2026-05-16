-- Migration: 0015_invitation_policies.sql
-- RLS policies de escrita para convites + função de aceitação de convite

-- DOWN:
-- DROP FUNCTION IF EXISTS accept_invitation(TEXT, UUID);
-- DROP POLICY IF EXISTS invitations_admin_update ON invitations;
-- DROP POLICY IF EXISTS invitations_admin_insert ON invitations;

-- ─── 1. Policy de INSERT — apenas owner/admin do tenant podem convidar ──────
CREATE POLICY invitations_admin_insert ON invitations
FOR INSERT WITH CHECK (
    tenant_id IN (
        SELECT tenant_id FROM user_tenants
        WHERE user_id = auth.uid() AND is_active = TRUE AND role IN ('owner', 'admin')
    )
);

-- ─── 2. Policy de UPDATE — owner/admin podem cancelar/atualizar convites ────
CREATE POLICY invitations_admin_update ON invitations
FOR UPDATE USING (
    tenant_id IN (
        SELECT tenant_id FROM user_tenants
        WHERE user_id = auth.uid() AND is_active = TRUE AND role IN ('owner', 'admin')
    )
);

-- ─── 3. Função de aceitação de convite ───────────────────────────────────────
-- Chamada pelo backend (SECURITY DEFINER), não pelo usuário diretamente.
-- Valida o token, verifica se o usuário já é membro e adiciona o vínculo.
CREATE OR REPLACE FUNCTION accept_invitation(p_token TEXT, p_user_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_inv invitations%ROWTYPE;
    v_already_member BOOLEAN;
BEGIN
    -- Buscar convite pendente e não expirado
    SELECT * INTO v_inv
    FROM invitations
    WHERE token = p_token
      AND status = 'pending'
      AND expires_at > NOW();

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'Convite inválido ou expirado');
    END IF;

    -- Verificar se o usuário já é membro ativo
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

    -- Adicionar como membro (ou reativar se havia sido removido)
    INSERT INTO user_tenants (user_id, tenant_id, role)
    VALUES (p_user_id, v_inv.tenant_id, v_inv.role)
    ON CONFLICT (user_id, tenant_id)
    DO UPDATE SET role = EXCLUDED.role, is_active = TRUE, updated_at = NOW();

    -- Marcar convite como aceito
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
