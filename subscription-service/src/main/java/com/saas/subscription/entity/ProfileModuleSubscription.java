package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Assinatura de um módulo por um perfil (tenant). Chave lógica única
 * (tenant_id, module_id) — ver ProfileModuleSubscriptionRepository.upsert,
 * que usa INSERT ... ON CONFLICT nativo para preservar a atomicidade contra
 * concorrência (dois requests simultâneos contratando o mesmo módulo).
 */
@Entity
@Table(name = "profile_module_subscriptions")
public class ProfileModuleSubscription extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "module_id", nullable = false)
    public UUID moduleId;

    @Column(name = "plan_version_id", nullable = false)
    public UUID planVersionId;

    /** MONTHLY | ANNUAL | FREE */
    @Column(name = "billing_cycle", nullable = false)
    public String billingCycle;

    /** TRIAL | TRIAL_CANCELLED | ACTIVE | PENDING_PAYMENT | CANCELED | EXPIRED */
    @Column(nullable = false)
    public String status;

    @Column(name = "started_at", nullable = false)
    public OffsetDateTime startedAt;

    @Column(name = "expires_at")
    public OffsetDateTime expiresAt;

    @Column(name = "canceled_at")
    public OffsetDateTime canceledAt;

    @Column(name = "created_by_user_id", nullable = false)
    public UUID createdByUserId;

    @Column(name = "trial_days")
    public Integer trialDays;

    @Column(name = "trial_start_at")
    public OffsetDateTime trialStartAt;

    @Column(name = "trial_end_at")
    public OffsetDateTime trialEndAt;

    @Column(name = "billing_starts_at")
    public OffsetDateTime billingStartsAt;

    @Column(name = "trial_campaign_id")
    public UUID trialCampaignId;

    @Column(name = "trial_history_id")
    public UUID trialHistoryId;

    public boolean isUsable() {
        boolean statusOk = "ACTIVE".equals(status) || "TRIAL".equals(status) || "TRIAL_CANCELLED".equals(status);
        boolean notExpired = expiresAt == null || expiresAt.isAfter(OffsetDateTime.now());
        return statusOk && notExpired;
    }
}
