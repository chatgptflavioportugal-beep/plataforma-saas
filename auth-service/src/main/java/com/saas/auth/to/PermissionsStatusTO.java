package com.saas.auth.to;

import com.saas.platformdatabase.annotations.Column;

/**
 * TO da camada de dados para o status de permissoes do vinculo usuario-tenant (tabela
 * {@code user_tenants}) — usado para decidir se o PAT/MAT em cache no frontend ainda e
 * valido (ver {@code UserTenantDAO.resolvePermissionsVersion}).
 */
public class PermissionsStatusTO {

    @Column(name = "permissions_version")
    private Integer permissionsVersion;

    @Column(name = "is_active")
    private Boolean active;

    public Integer permissionsVersion() {
        return permissionsVersion;
    }

    public Boolean active() {
        return active;
    }
}
