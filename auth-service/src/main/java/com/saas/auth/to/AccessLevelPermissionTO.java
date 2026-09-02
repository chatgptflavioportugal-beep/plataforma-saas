package com.saas.auth.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para uma permissao administrativa de um nivel de acesso. */
public class AccessLevelPermissionTO {

    @Column(name = "access_level_id")
    private String accessLevelId;

    @Column(name = "permission_key")
    private String permissionKey;

    public AccessLevelPermissionTO() {
    }

    public AccessLevelPermissionTO(String accessLevelId, String permissionKey) {
        this.accessLevelId = accessLevelId;
        this.permissionKey = permissionKey;
    }

    public String accessLevelId() {
        return accessLevelId;
    }

    public String permissionKey() {
        return permissionKey;
    }
}
