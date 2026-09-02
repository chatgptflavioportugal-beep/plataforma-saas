package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para a checagem de autorizacao administrativa de um usuario. */
public class AdminAuthProfileTO {

    @Column(name = "system_role") private String systemRole;
    @Column(name = "is_active") private Boolean isActive;
    @Column(name = "admin_access_level_id") private String adminAccessLevelId;

    public String systemRole() { return systemRole; }
    public Boolean isActive() { return isActive; }
    public String adminAccessLevelId() { return adminAccessLevelId; }
}
