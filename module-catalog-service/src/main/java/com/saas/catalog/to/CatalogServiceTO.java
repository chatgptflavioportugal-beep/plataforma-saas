package com.saas.catalog.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para um servico do catalogo resolvido por route key. */
public class CatalogServiceTO {

    @Column(name = "service_id")
    private String serviceId;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "route_key")
    private String routeKey;

    @Column(name = "module_id")
    private String moduleId;

    @Column(name = "module_name")
    private String moduleName;

    @Column(name = "module_slug")
    private String moduleSlug;

    @Column(name = "service_slug")
    private String serviceSlug;

    @Column(name = "group_slug")
    private String groupSlug;

    public CatalogServiceTO() {
    }

    public CatalogServiceTO(String serviceId, String serviceName, String routeKey, String moduleId,
            String moduleName, String moduleSlug, String serviceSlug, String groupSlug) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.routeKey = routeKey;
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.moduleSlug = moduleSlug;
        this.serviceSlug = serviceSlug;
        this.groupSlug = groupSlug;
    }

    public String serviceId() { return serviceId; }
    public String serviceName() { return serviceName; }
    public String routeKey() { return routeKey; }
    public String moduleId() { return moduleId; }
    public String moduleName() { return moduleName; }
    public String moduleSlug() { return moduleSlug; }
    public String serviceSlug() { return serviceSlug; }
    public String groupSlug() { return groupSlug; }
}
