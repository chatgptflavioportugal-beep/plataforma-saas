package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para os campos basicos de um nivel de acesso admin (sem permissoes). */
public class AccessLevelBasicTO {

    @Column(name = "id") private String id;
    @Column(name = "name") private String name;
    @Column(name = "description") private String description;
    @Column(name = "status") private String status;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "updated_at") private String updatedAt;

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public String status() { return status; }
    public String createdAt() { return createdAt; }
    public String updatedAt() { return updatedAt; }
}
