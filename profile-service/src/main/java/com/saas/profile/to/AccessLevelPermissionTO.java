package com.saas.profile.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para uma permissao de servico de um nivel de acesso. */
public class AccessLevelPermissionTO {

    @Column(name = "id")
    private String id;

    @Column(name = "module_id")
    private String moduleId;

    @Column(name = "service_id")
    private String serviceId;

    public String id() { return id; }
    public String moduleId() { return moduleId; }
    public String serviceId() { return serviceId; }
}
