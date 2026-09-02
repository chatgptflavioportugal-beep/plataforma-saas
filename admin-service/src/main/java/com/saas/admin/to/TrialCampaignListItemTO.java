package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para uma linha da listagem paginada de campanhas de Trial. */
public class TrialCampaignListItemTO extends TrialCampaignBaseTO {

    @Column(name = "plan_name") private String planName;
    @Column(name = "plan_code") private String planCode;
    @Column(name = "plan_version") private Integer planVersion;

    public String planName() { return planName; }
    public String planCode() { return planCode; }
    public Integer planVersion() { return planVersion; }
}
