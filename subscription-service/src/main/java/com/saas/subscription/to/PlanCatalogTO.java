package com.saas.subscription.to;

import com.saas.platformdatabase.annotations.Column;

import java.math.BigDecimal;
import java.util.UUID;

/** TO da camada de dados para o catalogo publico de planos (tabela {@code plans}). */
public class PlanCatalogTO {

    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "price_monthly")
    private BigDecimal priceMonthly;

    @Column(name = "price_annual")
    private BigDecimal priceAnnual;

    @Column(name = "discount_annual_percent")
    private BigDecimal discountAnnualPercent;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_ai_requests_month")
    private Integer maxAiRequestsMonth;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "version")
    private Integer version;

    @Column(name = "billing_type")
    private String billingType;

    @Column(name = "is_most_popular")
    private Boolean isMostPopular;

    @Column(name = "plan_type")
    private String planType;

    @Column(name = "total_monthly_price")
    private BigDecimal totalMonthlyPrice;

    @Column(name = "total_annual_monthly_price")
    private BigDecimal totalAnnualMonthlyPrice;

    @Column(name = "total_annual_price")
    private BigDecimal totalAnnualPrice;

    @Column(name = "module_count")
    private Integer moduleCount;

    @Column(name = "modules_json")
    private String modulesJson;

    public UUID id() { return id; }
    public String name() { return name; }
    public String code() { return code; }
    public String description() { return description; }
    public BigDecimal priceMonthly() { return priceMonthly; }
    public BigDecimal priceAnnual() { return priceAnnual; }
    public BigDecimal discountAnnualPercent() { return discountAnnualPercent; }
    public Integer maxUsers() { return maxUsers; }
    public Integer maxAiRequestsMonth() { return maxAiRequestsMonth; }
    public Integer sortOrder() { return sortOrder; }
    public Integer version() { return version; }
    public String billingType() { return billingType; }
    public Boolean isMostPopular() { return isMostPopular; }
    public String planType() { return planType; }
    public BigDecimal totalMonthlyPrice() { return totalMonthlyPrice; }
    public BigDecimal totalAnnualMonthlyPrice() { return totalAnnualMonthlyPrice; }
    public BigDecimal totalAnnualPrice() { return totalAnnualPrice; }
    public Integer moduleCount() { return moduleCount; }
    public String modulesJson() { return modulesJson; }
}
