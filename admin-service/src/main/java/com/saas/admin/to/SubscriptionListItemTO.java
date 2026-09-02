package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

import java.math.BigDecimal;

/** TO da camada de dados para uma linha da listagem administrativa de assinaturas de modulo. */
public class SubscriptionListItemTO {

    @Column(name = "id") private String id;
    @Column(name = "profile_id") private String profileId;
    @Column(name = "profile_name") private String profileName;
    @Column(name = "profile_type") private String profileType;
    @Column(name = "company_id") private String companyId;
    @Column(name = "company_name") private String companyName;
    @Column(name = "company_slug") private String companySlug;
    @Column(name = "owner_user_id") private String ownerUserId;
    @Column(name = "owner_name") private String ownerName;
    @Column(name = "owner_email") private String ownerEmail;
    @Column(name = "module_id") private String moduleId;
    @Column(name = "module_name") private String moduleName;
    @Column(name = "module_icon_path") private String moduleIconPath;
    @Column(name = "plan_id") private String planId;
    @Column(name = "plan_name") private String planName;
    @Column(name = "plan_version_id") private String planVersionId;
    @Column(name = "plan_version_number") private Integer planVersionNumber;
    @Column(name = "billing_cycle") private String billingCycle;
    @Column(name = "price") private BigDecimal price;
    @Column(name = "annual_total_price") private BigDecimal annualTotalPrice;
    @Column(name = "status") private String status;
    @Column(name = "started_at") private String startedAt;
    @Column(name = "expires_at") private String expiresAt;
    @Column(name = "canceled_at") private String canceledAt;
    @Column(name = "renewal_active") private Boolean renewalActive;
    @Column(name = "total_count") private Long totalCount;

    public String id() { return id; }
    public String profileId() { return profileId; }
    public String profileName() { return profileName; }
    public String profileType() { return profileType; }
    public String companyId() { return companyId; }
    public String companyName() { return companyName; }
    public String companySlug() { return companySlug; }
    public String ownerUserId() { return ownerUserId; }
    public String ownerName() { return ownerName; }
    public String ownerEmail() { return ownerEmail; }
    public String moduleId() { return moduleId; }
    public String moduleName() { return moduleName; }
    public String moduleIconPath() { return moduleIconPath; }
    public String planId() { return planId; }
    public String planName() { return planName; }
    public String planVersionId() { return planVersionId; }
    public Integer planVersionNumber() { return planVersionNumber; }
    public String billingCycle() { return billingCycle; }
    public BigDecimal price() { return price; }
    public BigDecimal annualTotalPrice() { return annualTotalPrice; }
    public String status() { return status; }
    public String startedAt() { return startedAt; }
    public String expiresAt() { return expiresAt; }
    public String canceledAt() { return canceledAt; }
    public Boolean renewalActive() { return renewalActive; }
    public long totalCount() { return totalCount != null ? totalCount : 0L; }
}
