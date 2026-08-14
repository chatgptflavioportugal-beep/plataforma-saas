package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "plan_version_module_limits")
public class PlanVersionModuleLimit extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "plan_version_module_id", nullable = false)
    public UUID planVersionModuleId;

    /** Estável entre upgrades/downgrades de plano: "<moduleSlug>.<limitCode>". */
    public String code;

    @Column(nullable = false)
    public String title;

    public String description;

    @Column(name = "limit_value")
    public String limitValue;

    public String unit;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;
}
