package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Permissão administrativa (chave fixa, ex.: "plans.subscribe") de um nível de acesso do perfil. */
@Entity
@Table(name = "profile_access_level_admin_permissions")
public class ProfileAccessLevelAdminPermission extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "access_level_id", nullable = false)
    public UUID accessLevelId;

    @Column(name = "permission_key", nullable = false)
    public String permissionKey;
}
