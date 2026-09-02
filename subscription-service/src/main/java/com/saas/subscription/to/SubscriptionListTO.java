package com.saas.subscription.to;

import com.saas.platformdatabase.annotations.Column;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** TO da camada de dados para uma linha da listagem de assinaturas de modulo de um tenant. */
public class SubscriptionListTO {

    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "profile_type")
    private String tenantType;

    @Column(name = "module_id")
    private UUID moduleId;

    @Column(name = "module_name")
    private String moduleName;

    @Column(name = "module_icon_path")
    private String moduleIconPath;

    @Column(name = "plan_version_id")
    private UUID planVersionId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "plan_name")
    private String planName;

    @Column(name = "plan_code")
    private String planCode;

    @Column(name = "plan_version")
    private Integer planVersion;

    @Column(name = "plan_sort_order")
    private Integer planSortOrder;

    @Column(name = "billing_cycle")
    private String billingCycle;

    @Column(name = "monthly_price")
    private BigDecimal monthlyPrice;

    @Column(name = "annual_monthly_price")
    private BigDecimal annualMonthlyPrice;

    @Column(name = "status")
    private String status;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;

    @Column(name = "trial_days")
    private Integer trialDays;

    @Column(name = "trial_start_at")
    private OffsetDateTime trialStartAt;

    @Column(name = "trial_end_at")
    private OffsetDateTime trialEndAt;

    @Column(name = "billing_starts_at")
    private OffsetDateTime billingStartsAt;

    @Column(name = "trial_campaign_id")
    private UUID trialCampaignId;

    @Column(name = "limits_json")
    private String limitsJson;

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String tenantType() { return tenantType; }
    public UUID moduleId() { return moduleId; }
    public String moduleName() { return moduleName; }
    public String moduleIconPath() { return moduleIconPath; }
    public UUID planVersionId() { return planVersionId; }
    public UUID planId() { return planId; }
    public String planName() { return planName; }
    public String planCode() { return planCode; }
    public Integer planVersion() { return planVersion; }
    public Integer planSortOrder() { return planSortOrder; }
    public String billingCycle() { return billingCycle; }
    public BigDecimal monthlyPrice() { return monthlyPrice; }
    public BigDecimal annualMonthlyPrice() { return annualMonthlyPrice; }
    public String status() { return status; }
    public OffsetDateTime startedAt() { return startedAt; }
    public OffsetDateTime expiresAt() { return expiresAt; }
    public OffsetDateTime canceledAt() { return canceledAt; }
    public Integer trialDays() { return trialDays; }
    public OffsetDateTime trialStartAt() { return trialStartAt; }
    public OffsetDateTime trialEndAt() { return trialEndAt; }
    public OffsetDateTime billingStartsAt() { return billingStartsAt; }
    public UUID trialCampaignId() { return trialCampaignId; }
    public String limitsJson() { return limitsJson; }
}
