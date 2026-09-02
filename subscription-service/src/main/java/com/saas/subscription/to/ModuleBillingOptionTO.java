package com.saas.subscription.to;

import com.saas.platformdatabase.annotations.Column;

import java.util.UUID;

/** TO da camada de dados para um modulo com suas opcoes de plano disponiveis. */
public class ModuleBillingOptionTO {

    @Column(name = "id")
    private UUID moduleId;

    @Column(name = "name")
    private String moduleName;

    @Column(name = "slug")
    private String moduleSlug;

    @Column(name = "description")
    private String moduleDescription;

    @Column(name = "icon_path")
    private String iconPath;

    @Column(name = "services_json")
    private String servicesJson;

    @Column(name = "available_plans_json")
    private String availablePlansJson;

    public UUID moduleId() { return moduleId; }
    public String moduleName() { return moduleName; }
    public String moduleSlug() { return moduleSlug; }
    public String moduleDescription() { return moduleDescription; }
    public String iconPath() { return iconPath; }
    public String servicesJson() { return servicesJson; }
    public String availablePlansJson() { return availablePlansJson; }
}
