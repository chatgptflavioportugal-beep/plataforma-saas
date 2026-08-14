package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Assinatura "principal" (legada) do tenant, usada apenas como fallback de acesso
 * em ModuleAccessResource — ver TenantSubscriptionRepository.
 */
@Entity
@Table(name = "tenant_subscriptions")
public class TenantSubscription extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    public UUID planId;

    @Column(nullable = false)
    public String status;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;
}
