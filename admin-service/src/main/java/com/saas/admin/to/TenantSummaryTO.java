package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o resumo administrativo de um tenant (empresa). */
public class TenantSummaryTO {

    @Column(name = "id") private String id;
    @Column(name = "name") private String name;
    @Column(name = "slug") private String slug;
    @Column(name = "status") private String status;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "trial_ends_at") private String trialEndsAt;
    @Column(name = "plan_name") private String planName;
    @Column(name = "plan_code") private String planCode;
    @Column(name = "subscription_status") private String subscriptionStatus;
    @Column(name = "owner_name") private String ownerName;
    @Column(name = "owner_email") private String ownerEmail;
    @Column(name = "member_count") private Integer memberCount;
    @Column(name = "pending_invitations_count") private Integer pendingInvitationsCount;

    public String id() { return id; }
    public String name() { return name; }
    public String slug() { return slug; }
    public String status() { return status; }
    public String createdAt() { return createdAt; }
    public String trialEndsAt() { return trialEndsAt; }
    public String planName() { return planName; }
    public String planCode() { return planCode; }
    public String subscriptionStatus() { return subscriptionStatus; }
    public String ownerName() { return ownerName; }
    public String ownerEmail() { return ownerEmail; }
    public Integer memberCount() { return memberCount; }
    public Integer pendingInvitationsCount() { return pendingInvitationsCount; }
}
