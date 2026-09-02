package com.saas.subscription.to;

import com.saas.platformdatabase.annotations.Column;

import java.time.OffsetDateTime;
import java.util.UUID;

/** TO da camada de dados para o status de acesso de um modulo no Dashboard. */
public class ModuleAccessStatusTO {

    @Column(name = "id")
    private UUID moduleId;

    @Column(name = "name")
    private String moduleName;

    @Column(name = "slug")
    private String moduleSlug;

    @Column(name = "description")
    private String moduleDescription;

    @Column(name = "icon_path")
    private String moduleIconPath;

    @Column(name = "sub_id")
    private UUID subscriptionId;

    @Column(name = "sub_status")
    private String subscriptionStatus;

    @Column(name = "sub_expires_at")
    private OffsetDateTime subscriptionExpiresAt;

    @Column(name = "sub_past_expiry")
    private Boolean pastExpiry;

    @Column(name = "plan_name")
    private String planName;

    @Column(name = "plan_slug")
    private String planSlug;

    @Column(name = "plan_version_id")
    private UUID planVersionId;

    @Column(name = "service_count")
    private Long serviceCount;

    @Column(name = "has_free_plan")
    private Integer hasFreePlanFlag;

    @Column(name = "trial_days_remaining")
    private Integer trialDaysRemaining;

    public UUID moduleId() { return moduleId; }
    public String moduleName() { return moduleName; }
    public String moduleSlug() { return moduleSlug; }
    public String moduleDescription() { return moduleDescription; }
    public String moduleIconPath() { return moduleIconPath; }
    public UUID subscriptionId() { return subscriptionId; }
    public String subscriptionStatus() { return subscriptionStatus; }
    public OffsetDateTime subscriptionExpiresAt() { return subscriptionExpiresAt; }
    public boolean pastExpiry() { return Boolean.TRUE.equals(pastExpiry); }
    public String planName() { return planName; }
    public String planSlug() { return planSlug; }
    public UUID planVersionId() { return planVersionId; }
    public long serviceCount() { return serviceCount != null ? serviceCount : 0L; }
    public boolean hasFreePlan() { return hasFreePlanFlag != null && hasFreePlanFlag == 1; }
    public Integer trialDaysRemaining() { return trialDaysRemaining; }
}
