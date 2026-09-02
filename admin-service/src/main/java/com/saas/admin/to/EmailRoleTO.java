package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o e-mail/system_role de um usuario administrativo. */
public class EmailRoleTO {

    @Column(name = "email") private String email;
    @Column(name = "system_role") private String systemRole;

    public String email() { return email; }
    public String systemRole() { return systemRole; }
}
