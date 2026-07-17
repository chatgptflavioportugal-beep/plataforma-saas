-- Migration: 0037_permissions_version_wiring.sql
-- Conclui a invalidação de permissions_version (0034): a reativação de um
-- membro via aceite de convite (accept_invitation) pode alterar role e
-- access_level_id de um vínculo já existente — precisa incrementar a versão
-- para invalidar ProfileAccessToken/ModuleAccessToken em cache no frontend.

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
        role                = EXCLUDED.role,
        access_level_id     = EXCLUDED.access_level_id,
        is_active           = TRUE,
        permissions_version = user_tenants.permissions_version + 1,
        updated_at          = NOW();

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
