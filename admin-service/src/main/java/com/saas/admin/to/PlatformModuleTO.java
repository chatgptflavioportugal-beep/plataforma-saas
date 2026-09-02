package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para um modulo da plataforma (tabela {@code platform_modules}). */
public class PlatformModuleTO {

    @Column(name = "id") private String id;
    @Column(name = "name") private String name;
    @Column(name = "slug") private String slug;
    @Column(name = "description") private String description;
    @Column(name = "module_url") private String moduleUrl;
    @Column(name = "icon_path") private String iconPath;
    @Column(name = "is_active") private Boolean isActive;
    @Column(name = "sort_order") private Integer sortOrder;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "updated_at") private String updatedAt;
    @Column(name = "service_count") private Integer serviceCount;

    public String id() { return id; }
    public String name() { return name; }
    public String slug() { return slug; }
    public String description() { return description; }
    public String moduleUrl() { return moduleUrl; }
    public String iconPath() { return iconPath; }
    public Boolean isActive() { return isActive; }
    public Integer sortOrder() { return sortOrder; }
    public String createdAt() { return createdAt; }
    public String updatedAt() { return updatedAt; }
    public Integer serviceCount() { return serviceCount; }
}
