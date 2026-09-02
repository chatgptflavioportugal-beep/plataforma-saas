package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para um grupo de servicos de modulo. */
public class PlatformModuleServiceGroupTO {

    @Column(name = "id") private String id;
    @Column(name = "module_id") private String moduleId;
    @Column(name = "name") private String name;
    @Column(name = "slug") private String slug;
    @Column(name = "description") private String description;
    @Column(name = "icon_path") private String iconPath;
    @Column(name = "sort_order") private Integer sortOrder;
    @Column(name = "status") private String status;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "updated_at") private String updatedAt;
    @Column(name = "service_count") private Integer serviceCount;

    public String id() { return id; }
    public String moduleId() { return moduleId; }
    public String name() { return name; }
    public String slug() { return slug; }
    public String description() { return description; }
    public String iconPath() { return iconPath; }
    public Integer sortOrder() { return sortOrder; }
    public String status() { return status; }
    public String createdAt() { return createdAt; }
    public String updatedAt() { return updatedAt; }
    public Integer serviceCount() { return serviceCount; }
}
