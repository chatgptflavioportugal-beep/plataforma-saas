package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

import java.math.BigDecimal;

/** TO da camada de dados para o resumo administrativo de um plano (tabela {@code plans}). */
public class PlanSummaryTO {

    @Column(name = "id") private String id;
    @Column(name = "name") private String name;
    @Column(name = "code") private String code;
    @Column(name = "description") private String description;
    @Column(name = "price_monthly") private BigDecimal priceMonthly;
    @Column(name = "price_annual") private BigDecimal priceAnnual;
    @Column(name = "discount_annual_percent") private Integer discountAnnualPercent;
    @Column(name = "max_users") private Integer maxUsers;
    @Column(name = "max_ai_requests_month") private Integer maxAiRequestsMonth;
    @Column(name = "is_active") private Boolean isActive;
    @Column(name = "sort_order") private Integer sortOrder;
    @Column(name = "version") private Integer version;
    @Column(name = "is_current_version") private Boolean isCurrentVersion;
    @Column(name = "parent_plan_id") private String parentPlanId;
    @Column(name = "billing_type") private String billingType;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "is_most_popular") private Boolean isMostPopular;
    @Column(name = "plan_type") private String planType;
    @Column(name = "paid_subscriptions") private Long paidSubscriptions;
    @Column(name = "trial_subscriptions") private Long trialSubscriptions;
    @Column(name = "total_monthly_price") private BigDecimal totalMonthlyPrice;
    @Column(name = "total_annual_monthly_price") private BigDecimal totalAnnualMonthlyPrice;
    @Column(name = "total_annual_price") private BigDecimal totalAnnualPrice;
    @Column(name = "module_count") private Integer moduleCount;

    public String id() { return id; }
    public String name() { return name; }
    public String code() { return code; }
    public String description() { return description; }
    public BigDecimal priceMonthly() { return priceMonthly; }
    public BigDecimal priceAnnual() { return priceAnnual; }
    public Integer discountAnnualPercent() { return discountAnnualPercent; }
    public Integer maxUsers() { return maxUsers; }
    public Integer maxAiRequestsMonth() { return maxAiRequestsMonth; }
    public Boolean isActive() { return isActive; }
    public Integer sortOrder() { return sortOrder; }
    public Integer version() { return version; }
    public Boolean isCurrentVersion() { return isCurrentVersion; }
    public String parentPlanId() { return parentPlanId; }
    public String billingType() { return billingType; }
    public String createdAt() { return createdAt; }
    public Boolean isMostPopular() { return isMostPopular; }
    public String planType() { return planType; }
    public Long paidSubscriptions() { return paidSubscriptions; }
    public Long trialSubscriptions() { return trialSubscriptions; }
    public BigDecimal totalMonthlyPrice() { return totalMonthlyPrice; }
    public BigDecimal totalAnnualMonthlyPrice() { return totalAnnualMonthlyPrice; }
    public BigDecimal totalAnnualPrice() { return totalAnnualPrice; }
    public Integer moduleCount() { return moduleCount; }
}
