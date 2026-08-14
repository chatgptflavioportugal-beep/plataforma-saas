package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Ledger append-only de participação em Trial por módulo/tenant — nunca é removido. */
@Entity
@Table(name = "module_trial_history")
public class ModuleTrialHistory extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "module_id", nullable = false)
    public UUID moduleId;

    @Column(name = "plan_version_module_id", nullable = false)
    public UUID planVersionModuleId;

    @Column(name = "trial_campaign_id")
    public UUID trialCampaignId;

    @Column(name = "started_by_user_id")
    public UUID startedByUserId;

    @Column(name = "trial_started_at", nullable = false)
    public OffsetDateTime trialStartedAt;

    @Column(name = "trial_finished_at")
    public OffsetDateTime trialFinishedAt;

    @Column(name = "trial_canceled_at")
    public OffsetDateTime trialCanceledAt;

    @Column(name = "became_customer", nullable = false)
    public boolean becameCustomer;
}
