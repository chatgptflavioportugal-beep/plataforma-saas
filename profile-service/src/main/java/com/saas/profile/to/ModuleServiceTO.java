package com.saas.profile.to;

import com.saas.platformdatabase.annotations.Column;

/**
 * TO da camada de dados para a arvore modulo -&gt; grupo (opcional) -&gt; servico disponivel
 * para um tenant.
 */
public class ModuleServiceTO {

    @Column(name = "module_id")
    private String moduleId;

    @Column(name = "module_name")
    private String moduleName;

    @Column(name = "module_slug")
    private String moduleSlug;

    @Column(name = "module_icon_path")
    private String moduleIconPath;

    @Column(name = "group_id")
    private String groupId;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "group_description")
    private String groupDescription;

    @Column(name = "group_icon_path")
    private String groupIconPath;

    @Column(name = "group_sort_order")
    private Integer groupSortOrder;

    @Column(name = "service_id")
    private String serviceId;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_slug")
    private String serviceSlug;

    @Column(name = "service_icon_path")
    private String serviceIconPath;

    @Column(name = "service_sort_order")
    private Integer serviceSortOrder;

    public String moduleId() { return moduleId; }
    public String moduleName() { return moduleName; }
    public String moduleSlug() { return moduleSlug; }
    public String moduleIconPath() { return moduleIconPath; }
    public String groupId() { return groupId; }
    public String groupName() { return groupName; }
    public String groupDescription() { return groupDescription; }
    public String groupIconPath() { return groupIconPath; }
    public Integer groupSortOrder() { return groupSortOrder; }
    public String serviceId() { return serviceId; }
    public String serviceName() { return serviceName; }
    public String serviceSlug() { return serviceSlug; }
    public String serviceIconPath() { return serviceIconPath; }
    public Integer serviceSortOrder() { return serviceSortOrder; }
}
