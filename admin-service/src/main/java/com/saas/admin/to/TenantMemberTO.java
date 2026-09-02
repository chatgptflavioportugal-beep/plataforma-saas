package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para um membro de um tenant, na visao administrativa. */
public class TenantMemberTO {

    @Column(name = "full_name") private String fullName;
    @Column(name = "email") private String email;
    @Column(name = "role") private String role;
    @Column(name = "is_active") private Boolean isActive;
    @Column(name = "created_at") private String createdAt;

    public String fullName() { return fullName; }
    public String email() { return email; }
    public String role() { return role; }
    public Boolean isActive() { return isActive; }
    public String createdAt() { return createdAt; }
}
