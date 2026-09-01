package com.saas.profile.negocio.impl;

import com.saas.profile.security.TenantContext;

import java.util.UUID;

/**
 * Checagens de autorização: permissão administrativa de tenant (owner/admin sempre
 * têm; membro depende do nível de acesso) e o flag de administrador de plataforma
 * (system_role SUPER_ADMIN/ADMIN_USER), usado para impedir que um usuário
 * administrativo vire membro de empresa cliente.
 */
public interface AdminAccessNegocio {

    boolean isPlatformAdmin(UUID userId);

    boolean hasAdminPerm(TenantContext tc, UUID tenantId, String permKey);

    void requireAdminPerm(TenantContext tc, UUID tenantId, String permKey);
}
