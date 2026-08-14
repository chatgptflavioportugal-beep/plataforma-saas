package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String code;

    public String description;

    @Column(name = "price_monthly", nullable = false)
    public BigDecimal priceMonthly;

    @Column(name = "price_annual")
    public BigDecimal priceAnnual;

    @Column(name = "discount_annual_percent", nullable = false)
    public BigDecimal discountAnnualPercent;

    @Column(name = "max_users", nullable = false)
    public int maxUsers;

    @Column(name = "max_ai_requests_month", nullable = false)
    public int maxAiRequestsMonth;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;

    @Column(nullable = false)
    public int version;

    /** monthly | annual | both */
    @Column(name = "billing_type", nullable = false)
    public String billingType;

    @Column(name = "is_most_popular", nullable = false)
    public boolean isMostPopular;

    /** individual | business */
    @Column(name = "plan_type", nullable = false)
    public String planType;

    @Column(name = "is_active", nullable = false)
    public boolean isActive;

    @Column(name = "is_current_version", nullable = false)
    public boolean isCurrentVersion;
}
