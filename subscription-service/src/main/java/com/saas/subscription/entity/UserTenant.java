package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_tenants")
public class UserTenant extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    /** owner | admin | member | finance */
    @Column(nullable = false)
    public String role;

    @Column(name = "access_level_id")
    public UUID accessLevelId;

    @Column(name = "is_active", nullable = false)
    public boolean isActive;

    @Column(name = "permissions_version", nullable = false)
    public int permissionsVersion;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;
}
