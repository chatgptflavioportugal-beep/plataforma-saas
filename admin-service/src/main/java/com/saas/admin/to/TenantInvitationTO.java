package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para um convite pendente de um tenant, na visao administrativa. */
public class TenantInvitationTO {

    @Column(name = "email") private String email;
    @Column(name = "role") private String role;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "invited_by_name") private String invitedByName;

    public String email() { return email; }
    public String role() { return role; }
    public String createdAt() { return createdAt; }
    public String invitedByName() { return invitedByName; }
}
