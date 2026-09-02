package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o snapshot da versao atual de um plano. */
public class CurrentPlanTO {

    @Column(name = "code") private String code;
    @Column(name = "max_users") private Integer maxUsers;
    @Column(name = "max_ai_requests_month") private Integer maxAiRequestsMonth;
    @Column(name = "version") private Integer version;
    @Column(name = "name") private String name;
    @Column(name = "description") private String description;
    @Column(name = "sort_order") private Integer sortOrder;
    @Column(name = "billing_type") private String billingType;
    @Column(name = "discount_annual_percent") private Integer discountAnnualPercent;
    @Column(name = "is_most_popular") private Boolean isMostPopular;
    @Column(name = "plan_type") private String planType;

    public String code() { return code; }
    public Integer maxUsers() { return maxUsers; }
    public Integer maxAiRequestsMonth() { return maxAiRequestsMonth; }
    public Integer version() { return version; }
    public String name() { return name; }
    public String description() { return description; }
    public Integer sortOrder() { return sortOrder; }
    public String billingType() { return billingType; }
    public Integer discountAnnualPercent() { return discountAnnualPercent; }
    public Boolean isMostPopular() { return isMostPopular; }
    public String planType() { return planType; }
}
