package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para um participante (tenant) de uma campanha de Trial. */
public class TrialCampaignParticipantTO {

    @Column(name = "id") private String tenantId;
    @Column(name = "name") private String tenantName;
    @Column(name = "type") private String tenantType;
    @Column(name = "full_name") private String fullName;
    @Column(name = "email") private String email;
    @Column(name = "trial_started_at") private String trialStartedAt;
    @Column(name = "trial_finished_at") private String trialFinishedAt;
    @Column(name = "trial_canceled_at") private String trialCanceledAt;
    @Column(name = "status") private String subscriptionStatus;
    @Column(name = "became_customer") private Boolean becameCustomer;

    public String tenantId() { return tenantId; }
    public String tenantName() { return tenantName; }
    public String tenantType() { return tenantType; }
    public String fullName() { return fullName; }
    public String email() { return email; }
    public String trialStartedAt() { return trialStartedAt; }
    public String trialFinishedAt() { return trialFinishedAt; }
    public String trialCanceledAt() { return trialCanceledAt; }
    public String subscriptionStatus() { return subscriptionStatus; }
    public Boolean becameCustomer() { return becameCustomer; }
}
