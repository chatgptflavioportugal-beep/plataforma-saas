package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o perfil individual (pessoal) de um cliente. */
public class IndividualProfileTO {

    @Column(name = "id") private String id;
    @Column(name = "name") private String name;
    @Column(name = "slug") private String slug;
    @Column(name = "status") private String status;
    @Column(name = "created_at") private String createdAt;

    public String id() { return id; }
    public String name() { return name; }
    public String slug() { return slug; }
    public String status() { return status; }
    public String createdAt() { return createdAt; }
}
