package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para um servico de modulo (tabela {@code platform_module_services}). */
public class PlatformModuleServiceTO {

    @Column(name = "id") private String id;
    @Column(name = "module_id") private String moduleId;
    @Column(name = "name") private String name;
    @Column(name = "slug") private String slug;
    @Column(name = "description") private String description;
    @Column(name = "icon_path") private String iconPath;
    @Column(name = "is_active") private Boolean isActive;
    @Column(name = "sort_order") private Integer sortOrder;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "updated_at") private String updatedAt;
    @Column(name = "service_group_id") private String serviceGroupId;
    @Column(name = "group_name") private String groupName;
    @Column(name = "group_slug") private String groupSlug;
    @Column(name = "route_key") private String routeKey;

    public String id() { return id; }
    public String moduleId() { return moduleId; }
    public String name() { return name; }
    public String slug() { return slug; }
    public String description() { return description; }
    public String iconPath() { return iconPath; }
    public Boolean isActive() { return isActive; }
    public Integer sortOrder() { return sortOrder; }
    public String createdAt() { return createdAt; }
    public String updatedAt() { return updatedAt; }
    public String serviceGroupId() { return serviceGroupId; }
    public String groupName() { return groupName; }
    public String groupSlug() { return groupSlug; }
    public String routeKey() { return routeKey; }
}
