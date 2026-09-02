package com.saas.profile.to;

import com.saas.platformdatabase.annotations.Column;

import java.util.UUID;

/** TO da camada de dados para o vinculo usuario-tenant (tabela {@code user_tenants}). */
public class UserTenantTO {

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "role")
    private String role;

    public UUID tenantId() {
        return tenantId;
    }

    public String role() {
        return role;
    }
}
