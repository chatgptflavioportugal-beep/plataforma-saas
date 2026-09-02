package com.saas.usage.to;

import com.saas.platformdatabase.annotations.Column;

import java.time.LocalDate;

/** TO da camada de dados para uma linha do resumo de uso (tabela {@code module_usage_counters}). */
public class UsageSummaryTO {

    @Column(name = "module_slug")
    private String moduleSlug;

    @Column(name = "metric_code")
    private String metricCode;

    @Column(name = "period_date")
    private LocalDate periodDate;

    @Column(name = "count")
    private Long count;

    public String moduleSlug() { return moduleSlug; }
    public String metricCode() { return metricCode; }
    public LocalDate periodDate() { return periodDate; }
    public long count() { return count != null ? count : 0L; }
}
