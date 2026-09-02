package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o detalhe administrativo de um tenant (empresa). */
public class TenantDetailRowTO {

    @Column(name = "id") private String id;
    @Column(name = "name") private String name;
    @Column(name = "slug") private String slug;
    @Column(name = "status") private String status;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "trial_ends_at") private String trialEndsAt;
    @Column(name = "plan_name") private String planName;
    @Column(name = "plan_code") private String planCode;
    @Column(name = "subscription_status") private String subscriptionStatus;
    @Column(name = "trial_start") private String trialStart;
    @Column(name = "trial_end") private String trialEnd;
    @Column(name = "current_period_start") private String currentPeriodStart;
    @Column(name = "current_period_end") private String currentPeriodEnd;
    @Column(name = "billing_type") private String billingType;
    @Column(name = "owner_name") private String ownerName;
    @Column(name = "owner_email") private String ownerEmail;
    @Column(name = "owner_joined_at") private String ownerJoinedAt;

    public String id() { return id; }
    public String name() { return name; }
    public String slug() { return slug; }
    public String status() { return status; }
    public String createdAt() { return createdAt; }
    public String trialEndsAt() { return trialEndsAt; }
    public String planName() { return planName; }
    public String planCode() { return planCode; }
    public String subscriptionStatus() { return subscriptionStatus; }
    public String trialStart() { return trialStart; }
    public String trialEnd() { return trialEnd; }
    public String currentPeriodStart() { return currentPeriodStart; }
    public String currentPeriodEnd() { return currentPeriodEnd; }
    public String billingType() { return billingType; }
    public String ownerName() { return ownerName; }
    public String ownerEmail() { return ownerEmail; }
    public String ownerJoinedAt() { return ownerJoinedAt; }
}
