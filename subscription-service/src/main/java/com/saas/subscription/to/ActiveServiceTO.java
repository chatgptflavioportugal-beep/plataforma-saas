package com.saas.subscription.to;

import com.saas.platformdatabase.annotations.Column;

import java.util.UUID;

/** TO da camada de dados para um servico ativo de um modulo, com grupo opcional. */
public class ActiveServiceTO {

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_slug")
    private String serviceSlug;

    @Column(name = "service_description")
    private String serviceDescription;

    @Column(name = "service_icon_path")
    private String serviceIconPath;

    @Column(name = "service_group_id")
    private UUID serviceGroupId;

    @Column(name = "service_group_name")
    private String serviceGroupName;

    @Column(name = "route_key")
    private String routeKey;

    public UUID serviceId() { return serviceId; }
    public String serviceName() { return serviceName; }
    public String serviceSlug() { return serviceSlug; }
    public String serviceDescription() { return serviceDescription; }
    public String serviceIconPath() { return serviceIconPath; }
    public UUID serviceGroupId() { return serviceGroupId; }
    public String serviceGroupName() { return serviceGroupName; }
    public String routeKey() { return routeKey; }
}
