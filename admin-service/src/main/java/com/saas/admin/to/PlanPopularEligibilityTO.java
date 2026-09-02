package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para elegibilidade de "mais popular" de um plano. */
public class PlanPopularEligibilityTO {

    @Column(name = "is_active") private Boolean isActive;
    @Column(name = "is_current_version") private Boolean isCurrentVersion;

    public Boolean isActive() { return isActive; }
    public Boolean isCurrentVersion() { return isCurrentVersion; }
}
