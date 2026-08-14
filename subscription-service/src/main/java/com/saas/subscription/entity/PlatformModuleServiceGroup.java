package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "platform_module_service_groups")
public class PlatformModuleServiceGroup extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "module_id", nullable = false)
    public UUID moduleId;

    @Column(nullable = false)
    public String name;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;

    /** ACTIVE | INACTIVE */
    @Column(nullable = false)
    public String status;
}
