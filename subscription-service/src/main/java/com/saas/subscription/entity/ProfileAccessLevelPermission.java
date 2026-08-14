package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Permissão de nível de acesso (por perfil/tenant) sobre um serviço de módulo. */
@Entity
@Table(name = "profile_access_level_permissions")
public class ProfileAccessLevelPermission extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "access_level_id", nullable = false)
    public UUID accessLevelId;

    @Column(name = "module_id", nullable = false)
    public UUID moduleId;

    @Column(name = "service_id", nullable = false)
    public UUID serviceId;
}
