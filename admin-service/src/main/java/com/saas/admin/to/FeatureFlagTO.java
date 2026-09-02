package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para uma feature flag. */
public class FeatureFlagTO {

    @Column(name = "id") private String id;
    @Column(name = "key") private String key;
    @Column(name = "name") private String name;
    @Column(name = "description") private String description;
    @Column(name = "is_enabled") private Boolean isEnabled;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "updated_at") private String updatedAt;

    public String id() { return id; }
    public String key() { return key; }
    public String name() { return name; }
    public String description() { return description; }
    public Boolean isEnabled() { return isEnabled; }
    public String createdAt() { return createdAt; }
    public String updatedAt() { return updatedAt; }
}
