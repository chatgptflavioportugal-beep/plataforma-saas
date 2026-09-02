package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para uma empresa (tenant) da qual o cliente e membro (nao owner). */
public class MemberCompanyTO {

    @Column(name = "id") private String id;
    @Column(name = "name") private String name;
    @Column(name = "slug") private String slug;
    @Column(name = "role") private String role;
    @Column(name = "is_active") private Boolean linkActive;
    @Column(name = "created_at") private String joinedAt;
    @Column(name = "invited_by_name") private String invitedByName;

    public String id() { return id; }
    public String name() { return name; }
    public String slug() { return slug; }
    public String role() { return role; }
    public Boolean linkActive() { return linkActive; }
    public String joinedAt() { return joinedAt; }
    public String invitedByName() { return invitedByName; }
}
