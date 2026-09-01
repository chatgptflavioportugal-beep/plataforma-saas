package com.saas.auth.negocio.impl;

import com.saas.auth.dto.ProfileTokenResponse;

import java.util.UUID;

/**
 * Orquestra a emissão do ProfileAccessToken (PAT): resolve o tipo de perfil
 * do tenant, carrega as permissões administrativas do usuário (owner/admin
 * têm todas; membros, as do seu nível de acesso) e assina o token.
 */
public interface ProfileTokenNegocio {

    ProfileTokenResponse issueAccessToken(UUID userId, UUID tenantId, String role);

    int permissionsVersion(UUID userId, UUID tenantId);
}
