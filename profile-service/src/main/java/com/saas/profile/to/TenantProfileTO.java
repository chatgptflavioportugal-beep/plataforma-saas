package com.saas.profile.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o perfil completo de um tenant (tenant + assinatura + plano). */
public class TenantProfileTO {

    @Column(name = "id")
    private String tenantId;

    @Column(name = "name")
    private String tenantName;

    @Column(name = "slug")
    private String tenantSlug;

    @Column(name = "status")
    private String tenantStatus;

    @Column(name = "type")
    private String tenantType;

    @Column(name = "trial_ends_at")
    private String trialEndsAt;

    @Column(name = "sub_id")
    private String subId;

    @Column(name = "sub_status")
    private String subStatus;

    @Column(name = "trial_end")
    private String trialEnd;

    @Column(name = "current_period_start")
    private String currentPeriodStart;

    @Column(name = "current_period_end")
    private String currentPeriodEnd;

    @Column(name = "billing_type")
    private String billingType;

    @Column(name = "plan_version")
    private Object planVersion;

    @Column(name = "plan_id")
    private String planId;

    @Column(name = "plan_name")
    private String planName;

    @Column(name = "plan_code")
    private String planCode;

    @Column(name = "plan_type")
    private String planType;

    @Column(name = "price_monthly")
    private Object priceMonthly;

    @Column(name = "price_annual")
    private Object priceAnnual;

    @Column(name = "max_users")
    private Object maxUsers;

    @Column(name = "max_ai_requests_month")
    private Object maxAiRequestsMonth;

    @Column(name = "features")
    private String features;

    @Column(name = "role")
    private String role;

    @Column(name = "total_monthly_price")
    private Object totalMonthlyPrice;

    @Column(name = "total_annual_monthly_price")
    private Object totalAnnualMonthlyPrice;

    @Column(name = "total_annual_price")
    private Object totalAnnualPrice;

    public String tenantId() { return tenantId; }
    public String tenantName() { return tenantName; }
    public String tenantSlug() { return tenantSlug; }
    public String tenantStatus() { return tenantStatus; }
    public String tenantType() { return tenantType; }
    public String trialEndsAt() { return trialEndsAt; }
    public String subId() { return subId; }
    public String subStatus() { return subStatus; }
    public String trialEnd() { return trialEnd; }
    public String currentPeriodStart() { return currentPeriodStart; }
    public String currentPeriodEnd() { return currentPeriodEnd; }
    public String billingType() { return billingType; }
    public Object planVersion() { return planVersion; }
    public String planId() { return planId; }
    public String planName() { return planName; }
    public String planCode() { return planCode; }
    public String planType() { return planType; }
    public Object priceMonthly() { return priceMonthly; }
    public Object priceAnnual() { return priceAnnual; }
    public Object maxUsers() { return maxUsers; }
    public Object maxAiRequestsMonth() { return maxAiRequestsMonth; }
    public String features() { return features; }
    public String role() { return role; }
    public Object totalMonthlyPrice() { return totalMonthlyPrice; }
    public Object totalAnnualMonthlyPrice() { return totalAnnualMonthlyPrice; }
    public Object totalAnnualPrice() { return totalAnnualPrice; }
}
