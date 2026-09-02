package com.saas.auth.to;

import com.saas.platformdatabase.annotations.Column;

import java.util.UUID;

/**
 * TO da camada de dados para o vinculo usuario-tenant (tabela {@code user_tenants}),
 * populado via {@code TupleTOMapper} a partir de uma native query — nunca sai do DAO
 * diretamente, quem consome vira DTO/contexto de seguranca na camada de Negocio.
 */
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
