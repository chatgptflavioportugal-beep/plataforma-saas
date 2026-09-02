package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para os flags de ativo/mais-popular de um plano. */
public class PlanActiveFlagsTO {

    @Column(name = "is_active") private Boolean isActive;
    @Column(name = "is_most_popular") private Boolean isMostPopular;

    public Boolean isActive() { return isActive; }
    public Boolean isMostPopular() { return isMostPopular; }
}
