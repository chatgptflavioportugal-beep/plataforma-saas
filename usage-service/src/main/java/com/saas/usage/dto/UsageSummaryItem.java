package com.saas.usage.dto;

import java.time.LocalDate;

public record UsageSummaryItem(
        String moduleSlug,
        String metricCode,
        LocalDate periodDate,
        long count
) {
}
