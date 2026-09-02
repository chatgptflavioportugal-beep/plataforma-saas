package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

import java.math.BigDecimal;

/** TO da camada de dados para um modulo dentro de uma versao de plano. */
public class PlanVersionModuleTO {

    @Column(name = "id") private String id;
    @Column(name = "plan_id") private String planId;
    @Column(name = "module_id") private String moduleId;
    @Column(name = "module_name") private String moduleName;
    @Column(name = "module_slug") private String moduleSlug;
    @Column(name = "module_icon_path") private String moduleIconPath;
    @Column(name = "monthly_price") private BigDecimal monthlyPrice;
    @Column(name = "annual_monthly_price") private BigDecimal annualMonthlyPrice;
    @Column(name = "status") private String status;
    @Column(name = "sort_order") private Integer sortOrder;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "updated_at") private String updatedAt;
    @Column(name = "limits_json") private String limitsJson;

    public String id() { return id; }
    public String planId() { return planId; }
    public String moduleId() { return moduleId; }
    public String moduleName() { return moduleName; }
    public String moduleSlug() { return moduleSlug; }
    public String moduleIconPath() { return moduleIconPath; }
    public BigDecimal monthlyPrice() { return monthlyPrice; }
    public BigDecimal annualMonthlyPrice() { return annualMonthlyPrice; }
    public String status() { return status; }
    public Integer sortOrder() { return sortOrder; }
    public String createdAt() { return createdAt; }
    public String updatedAt() { return updatedAt; }
    public String limitsJson() { return limitsJson; }
}
