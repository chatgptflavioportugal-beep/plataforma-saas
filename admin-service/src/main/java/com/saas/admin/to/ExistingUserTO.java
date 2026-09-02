package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o id/system_role de um usuario existente, por e-mail. */
public class ExistingUserTO {

    @Column(name = "id") private String id;
    @Column(name = "system_role") private String systemRole;

    public String id() { return id; }
    public String systemRole() { return systemRole; }
}
