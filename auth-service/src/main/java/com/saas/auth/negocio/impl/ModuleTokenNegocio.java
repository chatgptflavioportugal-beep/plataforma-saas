package com.saas.auth.negocio.impl;

import com.saas.auth.negocio.ModuleTokenResult;

import java.util.UUID;

/**
 * Orquestra a emissão do ModuleAccessToken (MAT): resolve o acesso do tenant
 * ao módulo no subscription-service (assinatura/plano/limites — domínio
 * daquele serviço), carrega as permissões de serviço do usuário dentro do
 * módulo (domínio de perfil/nível de acesso, deste serviço) e assina o token
 * com os claims já resolvidos.
 */
public interface ModuleTokenNegocio {

    ModuleTokenResult issue(UUID userId, UUID tenantId, String role, String moduleSlug, String authorization);
}
