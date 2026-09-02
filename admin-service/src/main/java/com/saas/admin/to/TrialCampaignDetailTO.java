package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

import java.math.BigDecimal;

/** TO da camada de dados para o detalhe de uma campanha de Trial. */
public class TrialCampaignDetailTO extends TrialCampaignBaseTO {

    @Column(name = "module_slug") private String moduleSlug;
    @Column(name = "module_icon") private String moduleIcon;
    @Column(name = "plan_name") private String planName;
    @Column(name = "plan_code") private String planCode;
    @Column(name = "plan_version") private Integer planVersion;
    @Column(name = "plan_monthly_price") private BigDecimal planMonthlyPrice;
    @Column(name = "plan_annual_price") private BigDecimal planAnnualPrice;

    public String moduleSlug() { return moduleSlug; }
    public String moduleIcon() { return moduleIcon; }
    public String planName() { return planName; }
    public String planCode() { return planCode; }
    public Integer planVersion() { return planVersion; }
    public BigDecimal planMonthlyPrice() { return planMonthlyPrice; }
    public BigDecimal planAnnualPrice() { return planAnnualPrice; }
}
