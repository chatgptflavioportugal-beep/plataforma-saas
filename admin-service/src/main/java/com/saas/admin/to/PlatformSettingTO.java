package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para uma configuracao da plataforma. */
public class PlatformSettingTO {

    @Column(name = "key") private String key;
    @Column(name = "value") private String value;
    @Column(name = "description") private String description;
    @Column(name = "updated_at") private String updatedAt;

    public String key() { return key; }
    public String value() { return value; }
    public String description() { return description; }
    public String updatedAt() { return updatedAt; }
}
