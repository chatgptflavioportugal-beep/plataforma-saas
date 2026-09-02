package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o e-mail de um usuario administrativo. */
public class AdminEmailTO {

    @Column(name = "email") private String email;

    public String email() { return email; }
}
