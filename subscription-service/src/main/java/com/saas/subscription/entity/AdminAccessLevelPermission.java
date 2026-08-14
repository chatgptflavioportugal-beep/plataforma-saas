package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "admin_access_level_permissions")
public class AdminAccessLevelPermission extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "access_level_id", nullable = false)
    public UUID accessLevelId;

    @Column(name = "permission_key", nullable = false)
    public String permissionKey;
}
