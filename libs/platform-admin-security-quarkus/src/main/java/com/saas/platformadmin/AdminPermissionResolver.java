package com.saas.platformadmin;

import java.util.UUID;

/**
 * Verifica se um nível de acesso administrativo possui uma permissão granular. Cada serviço
 * implementa isto sobre seu próprio DAO (SQL nativo ou Panache).
 */
public interface AdminPermissionResolver {

    boolean hasPermission(UUID adminAccessLevelId, String permissionKey);
}
