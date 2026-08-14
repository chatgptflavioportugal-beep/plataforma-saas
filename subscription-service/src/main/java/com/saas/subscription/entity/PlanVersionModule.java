package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Vincula um módulo a uma versão de plano, com preço próprio (plan_version_modules). */
@Entity
@Table(name = "plan_version_modules")
public class PlanVersionModule extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "plan_id", nullable = false)
    public UUID planId;

    @Column(name = "module_id", nullable = false)
    public UUID moduleId;

    /** Somente leitura — navegação para join com Plan (plan_id é a coluna que governa a escrita). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", insertable = false, updatable = false)
    public Plan plan;

    /** Somente leitura — navegação para join com PlatformModule (module_id governa a escrita). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", insertable = false, updatable = false)
    public PlatformModule module;

    @Column(name = "monthly_price", nullable = false)
    public BigDecimal monthlyPrice;

    @Column(name = "annual_monthly_price", nullable = false)
    public BigDecimal annualMonthlyPrice;

    /** active | inactive */
    @Column(nullable = false)
    public String status;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;
}
