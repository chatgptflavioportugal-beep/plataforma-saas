package com.saas.profile.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o tenant individual (pessoal) de um usuario. */
public class IndividualTenantTO {

    @Column(name = "id")
    private String id;

    @Column(name = "name")
    private String name;

    @Column(name = "slug")
    private String slug;

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String slug() {
        return slug;
    }
}
